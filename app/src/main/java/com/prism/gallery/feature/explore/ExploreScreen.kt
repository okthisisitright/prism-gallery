package com.prism.gallery.feature.explore

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prism.gallery.core.designsystem.*
import com.prism.gallery.core.model.*
import com.prism.gallery.feature.photos.PhotoGrid

@Composable fun ExploreScreen(items: List<MediaItem>, favorites: Set<String>, memberships: Map<Long, Set<String>>, categories: Map<Long, String>, query: String, onQuery: (String) -> Unit, kind: String, onKind: (String) -> Unit, selected: Set<String>, onSelect: (MediaItem) -> Unit, onOpen: (MediaItem, List<MediaItem>) -> Unit) {
    val preferences = LocalPrismPreferences.current
    val result = remember(items, favorites, memberships, categories, query, kind, preferences.oldest) { filterMedia(items, LibraryFilter(query, kind), favorites, memberships, categories, preferences.oldest) }
    Column(Modifier.fillMaxSize()) {
        GlassToolbar("Explore", "Find the moment you had in mind.")
        GlassSearch(query, onQuery, Modifier.padding(horizontal = 22.dp))
        Box(Modifier.padding(horizontal = 22.dp, vertical = 10.dp)) { GlassSegmentedControl(listOf("All", "Photos", "Videos", "Favorites", "Screenshots", "Downloads", "Large files", "Recent"), kind, onKind) }
        Text("${result.size} results · searches local metadata", Modifier.padding(horizontal = 24.dp, vertical = 5.dp), style = MaterialTheme.typography.bodySmall)
        if (result.isEmpty()) EmptyState(Icons.Outlined.Search, "Nothing found", "Try a filename, month, folder, or category name. Explore searches metadata; it does not recognize faces or objects.")
        else PhotoGrid(result, preferences.columns, "None", selected, favorites, onSelect, { onOpen(it, result) })
    }
}
