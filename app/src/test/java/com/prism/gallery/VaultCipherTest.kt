package com.prism.gallery

import com.prism.gallery.core.security.*
import org.junit.Assert.*
import org.junit.Test
import java.io.*
import javax.crypto.AEADBadTagException

class VaultCipherTest {
    @Test fun roundTripEmptyAndMultiBufferObjects() { for (size in listOf(0, 1, 65536, 190000)) { val key = VaultCipher.random(32); val input = VaultCipher.random(size); assertArrayEquals(input, VaultCipher.open(key, VaultCipher.seal(key, input, "object"), "object")) } }
    @Test fun nonceIsUniqueForEachObject() { val key = VaultCipher.random(32); assertFalse(VaultCipher.seal(key, byteArrayOf(1), "same").contentEquals(VaultCipher.seal(key, byteArrayOf(1), "same"))) }
    @Test fun tamperedTagFails() { val key = VaultCipher.random(32); val encrypted = VaultCipher.seal(key, "private".toByteArray(), "id"); encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte(); assertThrows(Exception::class.java) { VaultCipher.open(key, encrypted, "id") } }
    @Test fun swappedObjectIdentityFailsAuthentication() { val key = VaultCipher.random(32); val encrypted = VaultCipher.seal(key, byteArrayOf(2), "first"); assertThrows(Exception::class.java) { VaultCipher.open(key, encrypted, "second") } }
    @Test fun incorrectKeyFailsAuthentication() { val encrypted = VaultCipher.seal(VaultCipher.random(32), byteArrayOf(2), "id"); assertThrows(Exception::class.java) { VaultCipher.open(VaultCipher.random(32), encrypted, "id") } }
    @Test fun truncatedHeaderAndCiphertextFail() { val key = VaultCipher.random(32); val encrypted = VaultCipher.seal(key, byteArrayOf(2), "id"); for (size in listOf(1, 10, encrypted.size - 1)) assertThrows(Exception::class.java) { VaultCipher.open(key, encrypted.copyOf(size), "id") } }
    @Test fun safeLengthLimitIsEnforced() { val key = VaultCipher.random(32); val encrypted = VaultCipher.seal(key, ByteArray(1024), "id"); assertThrows(Exception::class.java) { VaultCipher.open(key, encrypted, "id", 100) } }
    @Test fun passphraseDerivationUsesSaltAndClearsInput() { val salt = VaultCipher.random(32); val password = "strong passphrase".toCharArray(); val derived = VaultCipher.derive(password, salt); assertTrue(password.all { it == '\u0000' }); assertArrayEquals(derived, VaultCipher.derive("strong passphrase".toCharArray(), salt)); assertFalse(derived.contentEquals(VaultCipher.derive("wrong passphrase".toCharArray(), salt))); assertFalse(derived.contentEquals(VaultCipher.derive("strong passphrase".toCharArray(), VaultCipher.random(32)))) }
    @Test fun verificationFailureNeverPersistsOrTouchesSource() { val dir = kotlin.io.path.createTempDirectory().toFile(); val source = File(dir, "source").apply { writeText("original") }; var persisted = false; try { assertThrows(Exception::class.java) { VerifiedImport(dir).commit("id", { it.writeText("ciphertext") }, { error("authentication failed") }, { persisted = true }) }; assertFalse(persisted); assertEquals("original", source.readText()); assertFalse(File(dir, "id.prism").exists()); assertFalse(File(dir, "id.pending").exists()) } finally { dir.deleteRecursively() } }
    @Test fun writeFailureRollsBackTemporaryData() { val dir = kotlin.io.path.createTempDirectory().toFile(); try { assertThrows(Exception::class.java) { VerifiedImport(dir).commit("id", { it.writeText("partial"); error("disk full") }, {}, {}) }; assertTrue(dir.listFiles()!!.isEmpty()) } finally { dir.deleteRecursively() } }
    @Test fun persistenceFailureRollsBackFinalizedFile() { val dir = kotlin.io.path.createTempDirectory().toFile(); try { assertThrows(Exception::class.java) { VerifiedImport(dir).commit("id", { it.writeText("ciphertext") }, {}, { error("database failure") }) }; assertTrue(dir.listFiles()!!.isEmpty()) } finally { dir.deleteRecursively() } }
    @Test fun commitHappensOnlyAfterVerificationAndKeepsSource() { val dir = kotlin.io.path.createTempDirectory().toFile(); val order = mutableListOf<String>(); try { VerifiedImport(dir).commit("id", { order += "write"; it.writeText("ciphertext") }, { order += "verify" }, { order += "persist"; assertTrue(it.exists()) }); assertEquals(listOf("write", "verify", "persist"), order) } finally { dir.deleteRecursively() } }
}
