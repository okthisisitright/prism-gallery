package com.prism.gallery

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.prism.gallery.core.media.MediaRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Uses only a newly inserted, test-owned image; never mutates existing media. Grants photo access; run on a clean AVD. */
@RunWith(AndroidJUnit4::class)
class MediaStoreIntegrationTest {
    @Test fun scannerReadsRealPublishedImageAndKeepsItsIdentity() = runBlocking {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val resolver = context.contentResolver
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val alreadyGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        fun permission(action: String) { ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("pm $action ${context.packageName} android.permission.READ_MEDIA_IMAGES")).use { it.readBytes() } }
        if (!alreadyGranted) permission("grant")
        val name = "PrismTest-${UUID.randomUUID()}.png"
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Prism Tests")
            put(MediaStore.Images.Media.IS_PENDING, 1)
            put(MediaStore.Images.Media.WIDTH, 20)
            put(MediaStore.Images.Media.HEIGHT, 40)
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
        }) ?: error("MediaStore insertion failed")
        try {
            val bitmap = Bitmap.createBitmap(20, 40, Bitmap.Config.ARGB_8888)
            resolver.openOutputStream(uri)!!.use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }; bitmap.recycle()
            assertEquals(1, resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null))
            val repository = MediaRepository(context)
            assertTrue(repository.access().images)
            val found = repository.load {}.single { it.name == name }
            assertFalse(found.isVideo); assertTrue(found.size > 0); assertTrue(found.folder.contains("Prism Tests"))
            resolver.openInputStream(android.net.Uri.parse(found.uri))!!.use { input -> val decoded = BitmapFactory.decodeStream(input)!!; assertEquals(20, decoded.width); assertEquals(40, decoded.height); decoded.recycle() }
            assertEquals(found.key, repository.load {}.single { it.name == name }.key)
        } finally { resolver.delete(uri, null, null) }
    }
}
