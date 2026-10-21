package com.prism.gallery.core.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import com.prism.gallery.core.database.*
import com.prism.gallery.core.model.MediaItem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.*
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class PrivateImage(val name: String, val mime: String, val bytes: ByteArray)
class VaultRepository(private val context: Context, private val dao: PrismDao) {
    private val directory = File(context.noBackupFilesDir, "vault").apply { mkdirs() }
    private val config = AtomicFile(File(directory, "credentials.json"))
    private val throttle = context.getSharedPreferences("vault_throttle", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private var master: ByteArray? = null
    @Volatile private var foreground = true
    @Synchronized fun setForeground(active: Boolean) { foreground = active; if (!active) lock() }
    @Volatile private var epoch = 0L
    @Volatile var lastTouch = android.os.SystemClock.elapsedRealtime()
        private set
    fun touch() { lastTouch = android.os.SystemClock.elapsedRealtime() }
    private val _unlocked = MutableStateFlow(false)
    val unlocked = _unlocked.asStateFlow()
    val configured get() = config.baseFile.exists()
    val biometricConfigured get() = configured && runCatching { readConfig().has("bio") }.getOrDefault(false)
    @Synchronized fun lock() { master?.fill(0); master = null; epoch++; _unlocked.value = false }
    @Synchronized private fun session(): Pair<ByteArray, Long> = (master?.copyOf() ?: error("Vault is locked")) to epoch
    @Synchronized private fun accept(key: ByteArray) { if (!foreground) { key.fill(0); error("Return to Prism and unlock again") }; master?.fill(0); master = key; epoch++; _unlocked.value = true }
    private fun readConfig() = JSONObject(config.openRead().bufferedReader().use { it.readText() })
    private fun writeConfig(json: JSONObject) {
        val stream = config.startWrite()
        try { stream.write(json.toString().toByteArray()); config.finishWrite(stream) } catch (e: Throwable) { config.failWrite(stream); throw e }
    }
    private fun bytes(value: String) = Base64.decode(value, Base64.NO_WRAP)
    private fun encoded(value: ByteArray) = Base64.encodeToString(value, Base64.NO_WRAP)
    private fun key(alias: String, biometric: Boolean = false): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val builder = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256)
        if (biometric) {
            builder.setUserAuthenticationRequired(true).setInvalidatedByBiometricEnrollment(true)
            if (Build.VERSION.SDK_INT >= 30) builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            else @Suppress("DEPRECATION") builder.setUserAuthenticationValidityDurationSeconds(-1)
        }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply { init(builder.build()) }.generateKey()
    }
    private fun deviceSeal(payload: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key("prism.device.v1")); updateAAD("prism.key.v1".toByteArray()) }
        return cipher.iv + cipher.doFinal(payload)
    }
    private fun deviceOpen(payload: ByteArray): ByteArray = Cipher.getInstance("AES/GCM/NoPadding").run {
        init(Cipher.DECRYPT_MODE, key("prism.device.v1"), GCMParameterSpec(128, payload.copyOfRange(0, 12)))
        updateAAD("prism.key.v1".toByteArray()); doFinal(payload, 12, payload.size - 12)
    }
    suspend fun create(password: CharArray) = withContext(Dispatchers.IO) { mutex.withLock {
        val requestedEpoch = epoch
        require(!configured) { "Vault already exists" }; require(password.size >= 8) { "Use at least 8 characters" }
        val salt = VaultCipher.random(32); val derived = VaultCipher.derive(password, salt); val generated = VaultCipher.random(32)
        try {
            val wrapped = VaultCipher.seal(derived, generated, "prism.master.v1")
            writeConfig(JSONObject().put("version", 1).put("salt", encoded(salt)).put("wrapped", encoded(deviceSeal(wrapped))))
            check(epoch == requestedEpoch) { "App left the foreground. Unlock again." }; touch(); accept(generated.copyOf())
        } finally { derived.fill(0); generated.fill(0) }
    } }
    suspend fun unlock(password: CharArray) = withContext(Dispatchers.IO) { mutex.withLock {
        val requestedEpoch = epoch
        val remaining = throttle.getLong("until", 0) - System.currentTimeMillis()
        require(remaining <= 0) { "Too many attempts. Try again in ${(remaining / 1000) + 1} seconds" }
        val json = readConfig(); require(json.getInt("version") == 1)
        val derived = VaultCipher.derive(password, bytes(json.getString("salt")))
        try {
            val wrapped = deviceOpen(bytes(json.getString("wrapped")))
            val opened = try { VaultCipher.open(derived, wrapped, "prism.master.v1", 64) } finally { wrapped.fill(0) }
            require(opened.size == 32); if (epoch != requestedEpoch) { opened.fill(0); error("App left the foreground. Unlock again.") }; touch(); accept(opened)
            throttle.edit().clear().commit()
        } catch (e: Exception) {
            val attempts = throttle.getInt("attempts", 0) + 1
            val delay = if (attempts >= 5) (30_000L * (1L shl (attempts - 5).coerceAtMost(6))) else 0L
            throttle.edit().putInt("attempts", attempts).putLong("until", System.currentTimeMillis() + delay).commit()
            throw IllegalArgumentException("Could not unlock: check your passphrase. A lost device key or damaged vault cannot be recovered.")
        } finally { derived.fill(0) }
    } }
    fun biometricCipher(enroll: Boolean): Cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
        if (enroll) { KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry("prism.biometric.v1") }; init(Cipher.ENCRYPT_MODE, key("prism.biometric.v1", true)) }
        else init(Cipher.DECRYPT_MODE, key("prism.biometric.v1", true), GCMParameterSpec(128, bytes(readConfig().getString("bioIv"))))
        updateAAD("prism.biometric.master.v1".toByteArray())
    }
    suspend fun biometricComplete(cipher: Cipher, enroll: Boolean) = withContext(Dispatchers.IO) { mutex.withLock {
        if (enroll) {
            val (secret, generation) = session()
            try { val wrapped = cipher.doFinal(secret); check(epoch == generation && _unlocked.value); writeConfig(readConfig().put("bio", encoded(wrapped)).put("bioIv", encoded(cipher.iv))) } finally { secret.fill(0) }
        } else {
            val opened = cipher.doFinal(bytes(readConfig().getString("bio")))
            require(opened.size == 32); touch(); accept(opened)
        }
    } }
    suspend fun disableBiometric() = withContext(Dispatchers.IO) { mutex.withLock { val json = readConfig(); json.remove("bio"); json.remove("bioIv"); writeConfig(json) } }

    /** Images only, capped before and while reading; video vault import is intentionally unavailable. */
    suspend fun import(item: MediaItem): VaultRecord = withContext(Dispatchers.IO) { mutex.withLock {
        require(!item.isVideo) { "Vault video import is not available" }
        require(item.size in 1..VaultCipher.MAX_IMAGE_BYTES.toLong()) { "Vault supports images up to 32 MiB" }
        val (secret, generation) = session(); val id = UUID.randomUUID().toString()
        var plaintext: ByteArray? = null
        val pending = File(directory, "$id.pending")
        val job = currentCoroutineContext()[Job]
        val record = VaultRecord(id, System.currentTimeMillis(), true)
        try {
            // A binary encrypted header keeps filenames and MIME out of the ordinary database.
            val header = ByteArrayOutputStream().also { out -> DataOutputStream(out).use { it.writeUTF(item.name.take(512)); it.writeUTF(item.mime); it.writeLong(item.size) } }.toByteArray()
            val digest = MessageDigest.getInstance("SHA-256")
            VerifiedImport(directory).commit(id, write = { temporary ->
            FileOutputStream(temporary).use { output ->
                val source = context.contentResolver.openInputStream(android.net.Uri.parse(item.uri)) ?: error("Source unavailable")
                source.use {
                    val limited = object : FilterInputStream(it) {
                        var total = 0L
                        override fun read(b: ByteArray, off: Int, len: Int): Int { job?.ensureActive(); val count = super.read(b, off, len); if (count > 0) { total += count; require(total <= VaultCipher.MAX_IMAGE_BYTES); digest.update(b, off, count) }; return count }
                    }
                    SequenceInputStream(ByteArrayInputStream(header), limited).use { input -> VaultCipher.encrypt(secret, input, output, id) }
                    require(limited.total == item.size) { "Source changed while importing" }
                }
                output.fd.sync()
            }
            }, verify = { temporary ->
            plaintext = FileInputStream(temporary).use { VaultCipher.decrypt(secret, it, id, VaultCipher.MAX_IMAGE_BYTES + 65536) }
            val verification = DataInputStream(ByteArrayInputStream(plaintext!!))
            verification.readUTF(); verification.readUTF(); require(verification.readLong() == item.size)
            val verifiedDigest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(65536)
            while (true) { val count = verification.read(buffer); if (count < 0) break; verifiedDigest.update(buffer, 0, count) }; buffer.fill(0)
            require(MessageDigest.isEqual(digest.digest(), verifiedDigest.digest())) { "Import verification failed" }
            job?.ensureActive(); check(epoch == generation && unlocked.value) { "Vault locked during import" }
            }, persist = {
                val fd = android.system.Os.open(directory.absolutePath, android.system.OsConstants.O_RDONLY, 0)
                try { android.system.Os.fsync(fd) } finally { android.system.Os.close(fd) }
                dao.addVaultBlocking(record)
            })
            record
        } finally { secret.fill(0); plaintext?.fill(0); pending.delete() }
    } }
    suspend fun image(id: String): PrivateImage = withContext(Dispatchers.IO) {
        require(UUID.fromString(id).toString() == id)
        val (secret, generation) = session(); var plaintext: ByteArray? = null
        try {
            plaintext = FileInputStream(File(directory, "$id.prism")).use { VaultCipher.decrypt(secret, it, id, VaultCipher.MAX_IMAGE_BYTES + 65536) }
            val input = DataInputStream(ByteArrayInputStream(plaintext)); val name = input.readUTF(); val mime = input.readUTF(); val size = input.readLong()
            require(size in 1..VaultCipher.MAX_IMAGE_BYTES.toLong() && input.available().toLong() == size)
            val photo = input.readBytes()
            if (epoch != generation || !unlocked.value) { photo.fill(0); error("Vault locked") }
            PrivateImage(name, mime, photo)
        } finally { secret.fill(0); plaintext?.fill(0) }
    }
    suspend fun delete(id: String) = withContext(Dispatchers.IO) { mutex.withLock {
        session().first.fill(0)
        require(UUID.fromString(id).toString() == id)
        val file = File(directory, "$id.prism")
        check(!file.exists() || file.delete()) { "Could not delete vault file" }; dao.deleteVault(id)
    } }
    suspend fun cleanInterruptedImports() = withContext(Dispatchers.IO) { mutex.withLock {
        val committed = dao.vaultIds().toSet()
        directory.listFiles()?.filter { it.extension == "pending" }?.forEach { it.delete() }
        // A crash after rename but before SQLite commit must never erase a verified copy.
        directory.listFiles()?.filter { it.extension == "prism" && it.nameWithoutExtension !in committed }?.forEach { file ->
            if (runCatching { UUID.fromString(file.nameWithoutExtension).toString() == file.nameWithoutExtension }.getOrDefault(false)) dao.addVault(VaultRecord(file.nameWithoutExtension, file.lastModified(), true))
        }
    } }
}
