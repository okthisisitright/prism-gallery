package com.prism.gallery.core.media

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.prism.gallery.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

/** Access is evaluated on every refresh; no permission result is persisted. */
data class Access(val images: Boolean, val videos: Boolean, val partial: Boolean) {
    val any get() = images || videos || partial
    val label get() = if (partial && !(images && videos)) "Selected media access" else if (images && videos) "Full library access" else if (images) "Photo access only" else if (videos) "Video access only" else "Media access needed"
}
fun mediaPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    Build.VERSION.SDK_INT >= 29 -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
}
class MediaRepository(private val context: Context) {
    fun access(): Access {
        fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
        return if (Build.VERSION.SDK_INT >= 33) Access(granted(Manifest.permission.READ_MEDIA_IMAGES), granted(Manifest.permission.READ_MEDIA_VIDEO), Build.VERSION.SDK_INT >= 34 && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED))
        else granted(Manifest.permission.READ_EXTERNAL_STORAGE).let { Access(it, it, false) }
    }
    fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) { override fun onChange(selfChange: Boolean) { trySend(Unit) } }
        context.contentResolver.registerContentObserver(MediaStore.Files.getContentUri("external"), true, observer)
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }.conflate()

    /** Metadata only; cursor rows are consumed in cancellable batches. Coil bounds thumbnail decodes. */
    suspend fun load(onBatch: suspend (List<MediaItem>) -> Unit): List<MediaItem> = withContext(Dispatchers.IO) {
        val access = access()
        if (!access.any) return@withContext emptyList()
        val results = ArrayList<MediaItem>()
        val volumes = if (Build.VERSION.SDK_INT >= 29) MediaStore.getExternalVolumeNames(context) else setOf("external")
        for (volume in volumes) {
            val version = if (Build.VERSION.SDK_INT >= 29) MediaStore.getVersion(context, volume).orEmpty() else "legacy"
            for (video in listOf(false, true)) {
                if (!access.partial && (if (video) !access.videos else !access.images)) continue
                val collection = if (video) MediaStore.Video.Media.getContentUri(volume) else MediaStore.Images.Media.getContentUri(volume)
                val columns = mutableListOf("_id", "_display_name", "mime_type", "_size", "width", "height", "datetaken", "date_added", "bucket_display_name")
                if (video) columns.add("duration")
                if (Build.VERSION.SDK_INT >= 29) columns.add("relative_path")
                if (Build.VERSION.SDK_INT >= 30) columns.add("generation_added")
                try {
                    context.contentResolver.query(collection, columns.toTypedArray(), if (Build.VERSION.SDK_INT >= 29) "is_pending = 0" else null, null, "date_added DESC")?.use { cursor ->
                        fun str(name: String) = cursor.getString(cursor.getColumnIndexOrThrow(name)).orEmpty()
                        fun number(name: String) = cursor.getLong(cursor.getColumnIndexOrThrow(name))
                        while (cursor.moveToNext()) {
                            currentCoroutineContext().ensureActive()
                            val id = number("_id"); val name = str("_display_name"); val size = number("_size"); val added = number("date_added") * 1000
                            val generation = if (Build.VERSION.SDK_INT >= 30) number("generation_added") else 0
                            results.add(MediaItem(mediaIdentity(volume, version, id, generation, added, name, size), ContentUris.withAppendedId(collection, id).toString(), name, str("mime_type").ifBlank { if (video) "video/*" else "image/*" }, size, number("width").toInt(), number("height").toInt(), number("datetaken").takeIf { it > 0 } ?: added, added, if (Build.VERSION.SDK_INT >= 29) str("relative_path").ifBlank { str("bucket_display_name") } else str("bucket_display_name"), if (video) number("duration") else 0))
                            if (results.size % 500 == 0) onBatch(results.toList())
                        }
                    }
                } catch (e: SecurityException) { if (!access().any) return@withContext emptyList() else throw e }
            }
        }
        results.sortedByDescending { it.date }
    }
}
