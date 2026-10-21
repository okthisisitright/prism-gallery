package com.prism.gallery.core.media

import android.content.*
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.prism.gallery.core.model.MediaItem
import com.prism.gallery.core.security.PrivateImage
import kotlinx.coroutines.*
import java.io.IOException

fun shareMedia(context: Context, items: List<MediaItem>, openWith: Boolean = false) {
    if (items.isEmpty()) return
    val uris = ArrayList(items.map { Uri.parse(it.uri) })
    val mime = if (items.all { it.isVideo }) "video/*" else if (items.all { !it.isVideo }) "image/*" else "*/*"
    val intent = if (openWith && items.size == 1) Intent(Intent.ACTION_VIEW).setDataAndType(uris.first(), items.first().mime)
    else if (items.size == 1) Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uris.first())
    else Intent(Intent.ACTION_SEND_MULTIPLE).setType(mime).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    intent.clipData = ClipData.newUri(context.contentResolver, "Selected media", uris.first()).also { clip -> uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) } }
    context.startActivity(Intent.createChooser(intent, if (openWith) "Open with" else "Share media"))
}
/** The SAF destination is user-selected. A failed partial copy is removed; source bytes are never mutated. */
suspend fun copyToTree(context: Context, tree: Uri, items: List<MediaItem>): Int = withContext(Dispatchers.IO) {
    val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    var count = 0
    for (item in items) {
        currentCoroutineContext().ensureActive()
        val target = DocumentsContract.createDocument(context.contentResolver, parent, item.mime, item.name) ?: error("Destination could not create a file")
        try {
            val bytes = context.contentResolver.openInputStream(Uri.parse(item.uri))!!.use { input -> context.contentResolver.openOutputStream(target, "w")!!.use { output -> input.copyTo(output) } }
            check(bytes == item.size) { "Source size changed during copy" }; count++
        } catch (e: Throwable) { runCatching { DocumentsContract.deleteDocument(context.contentResolver, target) }; throw e }
    }
    count
}
/** Restoring creates an explicitly public copy. Vault content is retained until a separate delete. */
suspend fun exportPrivate(context: Context, image: PrivateImage): Uri = withContext(Dispatchers.IO) {
    check(Build.VERSION.SDK_INT >= 29) { "Public vault export requires Android 10 or later in this version" }
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, image.name.substringAfterLast('/').substringAfterLast('\\'))
        put(MediaStore.Images.Media.MIME_TYPE, image.mime)
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Prism Restored")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: throw IOException("Could not create public copy")
    try {
        resolver.openOutputStream(uri, "w")!!.use { it.write(image.bytes) }
        check(resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null) == 1)
        uri
    } catch (e: Throwable) { resolver.delete(uri, null, null); throw e }
}
