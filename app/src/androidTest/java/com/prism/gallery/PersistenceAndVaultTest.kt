package com.prism.gallery

import android.content.*
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.prism.gallery.core.database.*
import com.prism.gallery.core.model.MediaItem
import com.prism.gallery.core.security.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PersistenceAndVaultTest {
    private lateinit var db: PrismDatabase
    private lateinit var context: Context
    private lateinit var privateDir: File
    @Before fun setUp() {
        val base = ApplicationProvider.getApplicationContext<Context>(); val unique = UUID.randomUUID().toString()
        privateDir = File(base.cacheDir, unique).apply { mkdirs() }
        context = object : ContextWrapper(base) { override fun getNoBackupFilesDir() = privateDir; override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences("$unique-$name", mode) }
        db = Room.inMemoryDatabaseBuilder(context, PrismDatabase::class.java).build()
    }
    @After fun tearDown() { db.close(); privateDir.deleteRecursively() }
    @Test fun categoriesAllowManyMembershipsAndCascadeOnlyMemberships() = runBlocking {
        val dao = db.dao(); val a = dao.create(Category(name = "Family")); val b = dao.create(Category(name = "Memes"))
        dao.categorize(a, listOf("same-photo", "same-photo")); dao.categorize(b, listOf("same-photo")); dao.favorite(Favorite("same-photo"))
        assertEquals(2, dao.memberships().first().size)
        dao.deleteCategory(a); assertEquals(listOf(Membership(b, "same-photo")), dao.memberships().first()); assertEquals(1, dao.favorites().first().size)
        dao.removeMembers(b, listOf("same-photo")); assertEquals(0, dao.memberships().first().size); assertEquals(1, dao.categories().first().size)
    }
    @Test fun categoryMetadataPersists() = runBlocking {
        val dao = db.dao(); val id = dao.create(Category(name = "Work", pinned = true, hidden = true, coverKey = "photo", accent = 2)); val row = dao.categories().first().single()
        assertEquals(id, row.id); assertTrue(row.pinned); assertTrue(row.hidden); assertEquals("photo", row.coverKey)
        dao.update(row.copy(name = "Receipts", position = 4)); assertEquals("Receipts", dao.categories().first().single().name)
    }
    @Test fun realVaultImportKeepsSourceLocksAndDetectsTampering() = runBlocking {
        val vault = VaultRepository(context, db.dao()); vault.create("a strong test passphrase".toCharArray())
        val source = File(privateDir, "source.png")
        Bitmap.createBitmap(20, 40, Bitmap.Config.ARGB_8888).let { bitmap -> source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle() }
        val item = MediaItem("id", Uri.fromFile(source).toString(), "source.png", "image/png", source.length(), 20, 40, 1, 1, "Test")
        val record = vault.import(item)
        assertTrue(source.exists()); assertTrue(record.sourceMayExist); assertEquals(1, db.dao().vault().first().size)
        val image = vault.image(record.id); assertArrayEquals(source.readBytes(), image.bytes); image.bytes.fill(0)
        val decoded = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.RED) }
        val immutable = decoded.copy(Bitmap.Config.ARGB_8888, false); decoded.recycle()
        val sensitive = vault.protectBitmap(immutable)
        assertTrue(sensitive.isMutable)
        vault.lock(); assertFalse(vault.unlocked.value); assertEquals(android.graphics.Color.TRANSPARENT, sensitive.getPixel(0, 0)); sensitive.recycle()
        try { vault.image(record.id); fail("Locked vault returned plaintext") } catch (_: IllegalStateException) { }
        try { vault.unlock("wrong passphrase".toCharArray()); fail("Wrong passphrase unlocked") } catch (_: IllegalArgumentException) { }
        vault.unlock("a strong test passphrase".toCharArray()); assertTrue(vault.unlocked.value)
        val file = File(privateDir, "vault/${record.id}.prism"); val damaged = file.readBytes(); damaged[damaged.lastIndex] = (damaged.last().toInt() xor 1).toByte(); file.writeBytes(damaged)
        try { vault.image(record.id); fail("Corrupted ciphertext returned plaintext") } catch (_: Exception) { }
        assertTrue(source.exists()); assertTrue(file.exists()); vault.lock()
    }
    @Test fun rejectedVideoImportCannotRemoveSource() = runBlocking {
        val vault = VaultRepository(context, db.dao()); vault.create("another strong passphrase".toCharArray())
        val source = File(privateDir, "source.mp4").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        try { vault.import(MediaItem("id", Uri.fromFile(source).toString(), "x.mp4", "video/mp4", 3, 10, 10, 1, 1, "Test")); fail("Video import should be gated") } catch (_: IllegalArgumentException) { }
        assertTrue(source.exists()); assertTrue(db.dao().vault().first().isEmpty()); vault.lock()
    }
}
