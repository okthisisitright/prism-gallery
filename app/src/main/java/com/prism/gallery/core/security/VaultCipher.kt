package com.prism.gallery.core.security

import java.io.*
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Versioned AES-256-GCM envelope. The caller supplies an object-specific authenticated context. */
object VaultCipher {
    const val MAX_IMAGE_BYTES = 32 * 1024 * 1024
    private val magic = byteArrayOf(0x50, 0x52, 0x53, 0x4d, 0x01)
    fun random(size: Int) = ByteArray(size).also { SecureRandom().nextBytes(it) }
    fun derive(password: CharArray, salt: ByteArray, iterations: Int = 600_000): ByteArray {
        require(salt.size >= 16 && iterations >= 600_000)
        val spec = PBEKeySpec(password, salt, iterations, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword(); password.fill('\u0000') }
    }
    fun seal(key: ByteArray, bytes: ByteArray, context: String): ByteArray {
        val output = ByteArrayOutputStream()
        encrypt(key, ByteArrayInputStream(bytes), output, context)
        return output.toByteArray()
    }
    fun open(key: ByteArray, bytes: ByteArray, context: String, max: Int = MAX_IMAGE_BYTES + 65536): ByteArray = decrypt(key, ByteArrayInputStream(bytes), context, max)
    fun encrypt(key: ByteArray, input: InputStream, output: OutputStream, context: String) {
        require(key.size == 32)
        val nonce = random(12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(magic + context.toByteArray(Charsets.UTF_8))
        output.write(magic); output.write(nonce)
        val buffer = ByteArray(64 * 1024)
        try {
            while (true) { val n = input.read(buffer); if (n < 0) break; cipher.update(buffer, 0, n)?.let(output::write) }
            output.write(cipher.doFinal())
        } finally { buffer.fill(0) }
    }
    /** No plaintext is returned until doFinal has authenticated the complete object. */
    fun decrypt(key: ByteArray, input: InputStream, context: String, max: Int): ByteArray {
        require(key.size == 32)
        val data = DataInputStream(input)
        val header = ByteArray(magic.size).also(data::readFully)
        require(header.contentEquals(magic)) { "Unsupported vault format" }
        val nonce = ByteArray(12).also(data::readFully)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(header + context.toByteArray(Charsets.UTF_8))
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var read = 0L
        try {
            while (true) {
                val n = data.read(buffer); if (n < 0) break
                read += n; require(read <= max.toLong() + 16) { "Vault object exceeds safe limit" }
                cipher.update(buffer, 0, n)?.let { require(output.size() + it.size <= max); output.write(it); it.fill(0) }
            }
            val final = cipher.doFinal()
            require(output.size() + final.size <= max)
            output.write(final); final.fill(0)
            return output.toByteArray()
        } finally { buffer.fill(0); output.reset() }
    }
}

/** Source deletion is deliberately outside this transaction. A committed copy is its only result. */
class VerifiedImport(private val directory: File) {
    fun commit(id: String, write: (File) -> Unit, verify: (File) -> Unit, persist: (File) -> Unit): File {
        directory.mkdirs()
        val temporary = File(directory, "$id.pending")
        val final = File(directory, "$id.prism")
        require(!final.exists())
        try {
            write(temporary)
            verify(temporary)
            check(temporary.renameTo(final)) { "Could not finalize vault file" }
            persist(final)
            return final
        } catch (e: Throwable) { temporary.delete(); final.delete(); throw e }
    }
}
