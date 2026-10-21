package com.prism.gallery.feature.vault

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prism.gallery.LibraryViewModel
import com.prism.gallery.core.database.VaultRecord
import com.prism.gallery.core.designsystem.*
import com.prism.gallery.core.media.exportPrivate
import com.prism.gallery.core.security.VaultRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.ByteArrayInputStream

private val decodeGate = Semaphore(2)
private suspend fun privateBitmap(vault: VaultRepository, id: String, target: Int): Pair<String, Bitmap> = decodeGate.withPermit { withContext(Dispatchers.IO) {
    val image = vault.image(id)
    try {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size, options)
        var sample = 1; while (options.outWidth / sample > target * 2 || options.outHeight / sample > target * 2) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size, BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }) ?: error("Unsupported image encoding")
        val orientation = ByteArrayInputStream(image.bytes).use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        val matrix = Matrix().apply { when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
        } }
        val bitmap = if (!matrix.isIdentity) Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also { if (it !== decoded) decoded.recycle() } else decoded
        image.name to bitmap
    } finally { image.bytes.fill(0) }
} }

@Composable fun GlassVaultLock(vm: LibraryViewModel, onBiometric: (Boolean) -> Unit) {
    val vault = vm.app.vault
    var configured by remember { mutableStateOf(vault.configured) }
    var password by remember { mutableStateOf("") }; var confirmation by remember { mutableStateOf("") }; var working by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 30.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Icon(Icons.Outlined.Lock, null, Modifier.padding(top = 28.dp).size(60.dp), tint = MaterialTheme.colorScheme.primary)
        Text(if (configured) "Just for you." else "A private place.", style = MaterialTheme.typography.displaySmall)
        Text(if (configured) "Unlock your encrypted vault." else "Choose a strong passphrase of at least 8 characters. Save it somewhere safe.", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), enabled = !working, label = { Text("Vault passphrase") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false), singleLine = true, shape = RoundedCornerShape(20.dp))
        if (!configured) OutlinedTextField(confirmation, { confirmation = it }, Modifier.fillMaxWidth(), enabled = !working, label = { Text("Confirm passphrase") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false), singleLine = true, shape = RoundedCornerShape(20.dp))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Button({
            if (!configured && password != confirmation) { error = "Passphrases do not match"; return@Button }
            val secret = password.toCharArray(); password = ""; confirmation = ""
            scope.launch { working = true; error = null; try { if (configured) vault.unlock(secret) else vault.create(secret); configured = vault.configured } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.localizedMessage; configured = vault.configured } finally { secret.fill('\u0000'); working = false } }
        }, enabled = !working && password.length >= 8, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { if (working) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(if (configured) "Unlock vault" else "Create vault") }
        if (configured && vault.biometricConfigured) OutlinedButton({ onBiometric(false) }, Modifier.fillMaxWidth(), enabled = !working) { Icon(Icons.Outlined.Fingerprint, null); Text(" Unlock with biometrics") }
        HorizontalDivider(Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = .2f))
        Text("Device-only protection", style = MaterialTheme.typography.titleMedium)
        Text("There is no recovery service or portable backup. A forgotten passphrase, lost Keystore key, reinstall, or clearing app data can permanently lose the vault. Biometrics are optional; keep your passphrase.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Images up to 32 MiB. Vault videos and direct private sharing are not supported in this version. Importing keeps the public original until you explicitly remove it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(40.dp))
    }
}
@Composable fun VaultScreen(vm: LibraryViewModel, onBack: () -> Unit, onBiometric: (Boolean) -> Unit, onChooseImport: () -> Unit) {
    val unlocked by vm.app.vault.unlocked.collectAsStateWithLifecycle(); val records by vm.vaultRecords.collectAsStateWithLifecycle(); val prefs by vm.preferences.collectAsStateWithLifecycle()
    var current by remember { mutableStateOf<VaultRecord?>(null) }; var selected by remember { mutableStateOf(setOf<String>()) }; var delete by remember { mutableStateOf(false) }; var export by remember { mutableStateOf(false) }; var info by remember { mutableStateOf(false) }
    LaunchedEffect(unlocked, prefs.vaultTimeout) {
        if (!unlocked) { current = null; selected = emptySet() }
        else while (true) { delay(1000); if (android.os.SystemClock.elapsedRealtime() - vm.app.vault.lastTouch >= prefs.vaultTimeout * 1000L) { vm.app.vault.lock(); break } }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onBack) { Icon(Icons.Outlined.ArrowBack, "Back") }; Text("Private Vault", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge); if (unlocked) { IconButton({ vm.app.vault.lock() }) { Icon(Icons.Outlined.Lock, "Lock now") } } }
        if (!unlocked) GlassVaultLock(vm, onBiometric)
        else if (current != null) {
            PrivateViewer(vm.app.vault, current!!, { current = null }, { selected = setOf(current!!.id); delete = true }, { selected = setOf(current!!.id); export = true })
        } else {
            Row(Modifier.padding(horizontal = 24.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { GlassButton("Import images", Icons.Outlined.AddPhotoAlternate, onClick = onChooseImport); GlassButton("Security", Icons.Outlined.Shield, onClick = { info = true }) }
            if (records.isEmpty()) EmptyState(Icons.Outlined.Lock, "Your private collection", "Import a photo after unlocking. It is encrypted and verified before source removal is offered.")
            LazyVerticalGrid(GridCells.Fixed(3), Modifier.weight(1f).padding(top = 16.dp), contentPadding = PaddingValues(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(records, key = { it.id }) { record -> PrivateTile(vm.app.vault, record, record.id in selected, selected.isNotEmpty(), { if (selected.isEmpty()) current = record else selected = selected.toggle(record.id) }, { selected = selected.toggle(record.id) }) }
            }
            if (selected.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) { TextButton({ selected = emptySet() }) { Text("Done (${selected.size})") }; TextButton({ export = true }) { Text("Restore") }; TextButton({ delete = true }) { Text("Delete") } }
        }
    }
    if (delete && unlocked) GlassDialog("Delete vault copies?", { delete = false }, { TextButton({ val ids = selected.toList(); delete = false; current = null; selected = emptySet(); vm.launchAction { ids.forEach { vm.app.vault.delete(it) }; vm.messages.send("Vault copies deleted") } }) { Text("Delete permanently") } }) { Text("Permanently remove ${selected.size} encrypted copies. Public originals, if any, are unaffected. Deleted vault files have no recovery.") }
    if (export && unlocked) GlassDialog("Restore public copies?", { export = false }, { TextButton({ val ids = selected.toList(); export = false; vm.launchAction { ids.forEach { id -> val image = vm.app.vault.image(id); try { exportPrivate(vm.app, image) } finally { image.bytes.fill(0) }; vm.dao.vaultSource(id, true) }; vm.refresh(); vm.messages.send("Restored to Pictures/Prism Restored. Public copies are visible to other gallery apps.") } }) { Text("Create public copies") } }) { Text("These images will be unencrypted in Pictures/Prism Restored and may be read or backed up by other apps. Encrypted copies remain in the vault. Android 10 or later is required.") }
    if (info && unlocked) GlassDialog("Vault security", { info = false }, { TextButton({ info = false }) { Text("Done") } }) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("AES-256-GCM protects image bytes and filenames. A passphrase-derived key and this device's Keystore protect the vault key. It locks when you leave the app, turn off the screen, or reach the idle timeout.", style = MaterialTheme.typography.bodySmall); Text("Biometric enrollment changes can invalidate biometric access. Use your passphrase instead. Removing app data or losing the device key is unrecoverable.", style = MaterialTheme.typography.bodySmall); GlassButton("Enable / replace biometrics", Icons.Outlined.Fingerprint, onClick = { onBiometric(true) }); TextButton({ vm.launchAction { vm.app.vault.disableBiometric(); vm.messages.send("Biometric unlock disabled") } }) { Text("Disable biometrics") } }
    }
}
private fun Set<String>.toggle(key: String) = if (key in this) this - key else this + key
@OptIn(ExperimentalFoundationApi::class)
@Composable private fun PrivateTile(vault: VaultRepository, record: VaultRecord, selected: Boolean, selecting: Boolean, onOpen: () -> Unit, onSelect: () -> Unit) {
    val result by produceState<Pair<String, Bitmap>?>(null, record.id) { try { value = privateBitmap(vault, record.id, 256) } catch (e: CancellationException) { throw e } catch (_: Exception) { } }
    DisposableEffect(result) { val bitmap = result?.second; onDispose { bitmap?.eraseColor(android.graphics.Color.TRANSPARENT) } }
    Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).combinedClickable(onClick = onOpen, onLongClick = onSelect)) {
        result?.let { Image(it.second.asImageBitmap(), "Private image", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } ?: Icon(Icons.Outlined.Lock, "Unavailable or loading", Modifier.align(Alignment.Center))
        if (record.sourceMayExist) Icon(Icons.Outlined.WarningAmber, "A public source may still exist", Modifier.align(Alignment.BottomStart).padding(5.dp).size(18.dp), tint = Color.White)
        if (selecting) Icon(if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, if (selected) "Selected" else "Unselected", Modifier.align(Alignment.TopEnd).padding(5.dp), tint = Color.White)
    }
}
@Composable private fun PrivateViewer(vault: VaultRepository, record: VaultRecord, onBack: () -> Unit, onDelete: () -> Unit, onExport: () -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    val result by produceState<Pair<String, Bitmap>?>(null, record.id) { try { value = privateBitmap(vault, record.id, 2048) } catch (e: CancellationException) { throw e } catch (_: Exception) { error = "This vault item could not be authenticated or decoded. Its encrypted copy has been kept." } }
    DisposableEffect(result) { val bitmap = result?.second; onDispose { bitmap?.eraseColor(android.graphics.Color.TRANSPARENT) } }
    Column(Modifier.fillMaxSize()) {
        TextButton(onBack) { Text("Back to vault") }
        Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF080B09))) { result?.let { Image(it.second.asImageBitmap(), "Private image", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }; error?.let { Text(it, Modifier.align(Alignment.Center).padding(24.dp), color = Color.White) } }
        Column(Modifier.padding(16.dp)) {
            Text(result?.first ?: "Encrypted image", style = MaterialTheme.typography.titleMedium)
            Text(if (record.sourceMayExist) "A plaintext original may still exist outside the vault." else "Source removal was confirmed by Android at import time. Other copies may exist.", style = MaterialTheme.typography.bodySmall)
            Row { TextButton(onExport) { Text("Restore public copy") }; TextButton(onDelete) { Text("Delete vault copy") } }
        }
    }
}
