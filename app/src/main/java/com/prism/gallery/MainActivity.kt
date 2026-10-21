package com.prism.gallery

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.*
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.prism.gallery.core.model.MediaItem
import kotlinx.coroutines.*

class MainActivity : FragmentActivity() {
    private val app get() = application as PrismApplication
    private var deletionResult: ((Boolean) -> Unit)? = null
    private var legacyDelete: Uri? = null
    private val deleteLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val callback = deletionResult; deletionResult = null
        val legacy = legacyDelete; legacyDelete = null
        if (result.resultCode != Activity.RESULT_OK) callback?.invoke(false)
        else if (legacy == null) callback?.invoke(true)
        else lifecycleScope.launch { val deleted = withContext(Dispatchers.IO) { runCatching { contentResolver.delete(legacy, null, null) > 0 }.getOrDefault(false) }; callback?.invoke(deleted) }
    }
    private val screenOff = object : BroadcastReceiver() { override fun onReceive(context: Context?, intent: Intent?) { app.vault.lock() } }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        ContextCompat.registerReceiver(this, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
        setContent { val vm: LibraryViewModel = viewModel(); PrismApp(vm, this, ::requestDeletion, ::biometric) }
    }
    override fun onUserInteraction() { super.onUserInteraction(); app.vault.touch() }
    override fun onStart() { super.onStart(); app.vault.setForeground(true) }
    override fun onStop() { app.vault.setForeground(false); super.onStop() }
    override fun onDestroy() { unregisterReceiver(screenOff); super.onDestroy() }
    fun secure(enabled: Boolean) { if (enabled) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    private fun requestDeletion(items: List<MediaItem>, permanently: Boolean, callback: (Boolean) -> Unit) {
        if (items.isEmpty()) return
        if (deletionResult != null) { callback(false); return }
        lifecycleScope.launch {
            try {
                if (Build.VERSION.SDK_INT >= 30) {
                    require(items.size <= 2000) { "Select at most 2,000 items per system request" }
                    val pending = withContext(Dispatchers.IO) { if (permanently) MediaStore.createDeleteRequest(contentResolver, items.map { Uri.parse(it.uri) }) else MediaStore.createTrashRequest(contentResolver, items.map { Uri.parse(it.uri) }, true) }
                    deletionResult = callback
                    deleteLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                } else {
                    // Android 9/10 have no batch trash API. Android 10 may require a recoverable grant.
                    require(items.size == 1) { "On Android 9/10 delete one item at a time" }
                    val uri = Uri.parse(items.first().uri)
                    try { val deleted = withContext(Dispatchers.IO) { contentResolver.delete(uri, null, null) > 0 }; callback(deleted) }
                    catch (e: SecurityException) {
                        if (Build.VERSION.SDK_INT == 29 && e is RecoverableSecurityException) {
                            deletionResult = callback; legacyDelete = uri
                            deleteLauncher.launch(IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build())
                        } else throw e
                    }
                }
            } catch (_: Exception) { deletionResult = null; legacyDelete = null; callback(false) }
        }
    }
    private fun biometric(enroll: Boolean, vm: LibraryViewModel) {
        if (BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) != BiometricManager.BIOMETRIC_SUCCESS) { vm.launchAction { vm.messages.send("Strong biometrics are unavailable. Use your vault passphrase.") }; return }
        try {
            val cipher = app.vault.biometricCipher(enroll)
            val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) { app.vault.lock(); return }
                    vm.launchAction { app.vault.biometricComplete(result.cryptoObject?.cipher ?: error("No cryptographic authorization"), enroll); vm.messages.send(if (enroll) "Biometric unlock enabled. Keep your passphrase." else "Vault unlocked") }
                }
                override fun onAuthenticationError(code: Int, message: CharSequence) { vm.launchAction { vm.messages.send("Biometric unlock canceled or unavailable. Use your passphrase.") } }
            })
            prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle(if (enroll) "Protect vault key with biometrics" else "Unlock Private Vault").setSubtitle("Prism Gallery · device-only encryption").setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG).setNegativeButtonText("Use passphrase").build(), BiometricPrompt.CryptoObject(cipher))
        } catch (_: Exception) { vm.launchAction { vm.messages.send("Biometric key is unavailable or invalidated. Unlock with your passphrase and enroll again.") } }
    }
}
