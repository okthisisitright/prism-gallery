package com.prism.gallery

import com.prism.gallery.core.model.*
import org.junit.Assert.*
import org.junit.Test

class MediaRulesTest {
    private fun photo(key: String = "a", date: Long = 1700000000000, name: String = "IMG.jpg", folder: String = "DCIM/Camera/") = MediaItem(key, "content://media/external/images/media/1", name, "image/jpeg", 4096, 4000, 3000, date, date, folder)
    @Test fun panoramaContainPreservesGeometry() { val (w, h) = containSize(12000f, 2000f, 1080f, 1920f); assertEquals(1080f, w, .01f); assertEquals(180f, h, .01f); assertEquals(6f, w / h, .001f) }
    @Test fun tallPhotoContainsWithoutStretch() { val (w, h) = containSize(100f, 1000f, 1080f, 1920f); assertEquals(192f, w, .01f); assertEquals(1920f, h, .01f) }
    @Test fun invalidBoundsAreSafe() { assertEquals(0f to 0f, containSize(0f, 100f, 100f, 100f)) }
    @Test fun reusedStoreIdGetsDifferentIdentity() { val first = mediaIdentity("external_primary", "v1", 1, 10, 170000L, "a.jpg", 30); assertNotEquals(first, mediaIdentity("external_primary", "v1", 1, 11, 170000L, "a.jpg", 30)); assertNotEquals(first, mediaIdentity("external_primary", "v2", 1, 10, 170000L, "a.jpg", 30)) }
    @Test fun identitiesSeparateVolumes() { assertNotEquals(mediaIdentity("sd", "1", 1, 0, 1, "x", 1), mediaIdentity("primary", "1", 1, 0, 1, "x", 1)) }
    @Test fun unchangedIdentityIsStable() { assertEquals(mediaIdentity("sd", "1", 1, 0, 1, "x", 1), mediaIdentity("sd", "1", 1, 0, 1, "x", 1)) }
    @Test fun categorySearchUsesMembership() { val items = listOf(photo("a"), photo("b")); assertEquals(listOf("b"), filterMedia(items, LibraryFilter(query = "Family"), emptySet(), mapOf(1L to setOf("b")), mapOf(1L to "Family Pictures"), false).map { it.key }) }
    @Test fun onePhotoCanMatchManyCategories() { val item = photo(); val memberships = mapOf(1L to setOf("a"), 2L to setOf("a")); for (id in listOf(1L, 2L)) assertEquals(listOf(item), filterMedia(listOf(item), LibraryFilter(category = id), emptySet(), memberships, emptyMap(), false)) }
    @Test fun filtersIntersectWithoutMutatingOriginals() { val items = listOf(photo("a"), photo("b", folder = "Download/")); assertEquals(emptyList<MediaItem>(), filterMedia(items, LibraryFilter(kind = "Downloads"), setOf("a"), emptyMap(), emptyMap(), false).filter { it.key == "a" }); assertEquals(2, items.size) }
    @Test fun favoritesOnlyIncludesKnownAccessibleMedia() { assertEquals(emptyList<MediaItem>(), filterMedia(listOf(photo()), LibraryFilter(kind = "Favorites"), setOf("unavailable"), emptyMap(), emptyMap(), false)) }
    @Test fun oldestSortIsDeterministic() { val a = photo("a", 1); val b = photo("b", 2); assertEquals(listOf(a, b), filterMedia(listOf(b, a), LibraryFilter(), emptySet(), emptyMap(), emptyMap(), true)) }
    @Test fun dateSearchWorks() { assertEquals(1, filterMedia(listOf(photo()), LibraryFilter(query = "2023"), emptySet(), emptyMap(), emptyMap(), false).size) }
    @Test fun screenshotsMatchFolderOrName() { val a = photo(name = "Screenshot_1.png"); assertEquals(listOf(a), filterMedia(listOf(a, photo("b")), LibraryFilter(kind = "Screenshots"), emptySet(), emptyMap(), emptyMap(), false)) }
}
