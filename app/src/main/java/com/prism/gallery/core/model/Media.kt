package com.prism.gallery.core.model

import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class MediaItem(
    val key: String, val uri: String, val name: String, val mime: String,
    val size: Long, val width: Int, val height: Int, val date: Long,
    val added: Long, val folder: String, val duration: Long = 0,
) { val isVideo: Boolean get() = mime.startsWith("video/") }

data class LibraryFilter(val query: String = "", val kind: String = "All", val category: Long? = null, val folder: String? = null)
fun filterMedia(items: List<MediaItem>, filter: LibraryFilter, favorites: Set<String>, memberships: Map<Long, Set<String>>, categoryNames: Map<Long, String>, oldest: Boolean): List<MediaItem> {
    val query = filter.query.trim().lowercase(Locale.ROOT)
    val result = items.filter { item ->
        (filter.folder == null || item.folder == filter.folder) &&
        (filter.category == null || item.key in memberships[filter.category].orEmpty()) &&
        when (filter.kind) {
            "Photos" -> !item.isVideo
            "Videos" -> item.isVideo
            "Favorites" -> item.key in favorites
            "Screenshots" -> "screenshot" in item.folder.lowercase() || "screenshot" in item.name.lowercase()
            "Downloads" -> "download" in item.folder.lowercase()
            "Large files" -> item.size >= 20L * 1024 * 1024
            "Recent" -> item.added > System.currentTimeMillis() - 7L * 86400_000
            else -> true
        } && (query.isBlank() || (item.name + " " + item.folder + " " + item.mime + " " + dateLabel(item.date, "yyyy MMMM dd") + " " + categoryNames.filterKeys { item.key in memberships[it].orEmpty() }.values.joinToString(" ")).lowercase(Locale.ROOT).contains(query))
    }
    return if (oldest) result.sortedBy { it.date } else result.sortedByDescending { it.date }
}
fun dateLabel(time: Long, pattern: String = "MMMM yyyy"): String = DateTimeFormatter.ofPattern(pattern).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(time))
fun mediaIdentity(volume: String, version: String, id: Long, generation: Long, added: Long, name: String, size: Long): String = MessageDigest.getInstance("SHA-256").digest("$volume|$version|$id|$generation|$added|$name|$size".toByteArray()).joinToString("") { "%02x".format(it) }
fun containSize(sourceWidth: Float, sourceHeight: Float, viewportWidth: Float, viewportHeight: Float): Pair<Float, Float> {
    if (sourceWidth <= 0 || sourceHeight <= 0 || viewportWidth <= 0 || viewportHeight <= 0) return 0f to 0f
    val scale = minOf(viewportWidth / sourceWidth, viewportHeight / sourceHeight)
    return sourceWidth * scale to sourceHeight * scale
}
