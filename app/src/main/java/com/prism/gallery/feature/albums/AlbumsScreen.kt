package com.prism.gallery.feature.albums

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.prism.gallery.core.database.Category
import com.prism.gallery.core.designsystem.*
import com.prism.gallery.core.model.*

@Composable fun GlassAlbumCard(category: Category, media: List<MediaItem>, onClick: () -> Unit, onEdit: () -> Unit) {
    Column(Modifier.widthIn(min = 130.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.15f).background(listOf(Color(0xFFD6DED0), Color(0xFFE2DACD), Color(0xFFCCD9DB), Color(0xFFDACFD6))[category.accent.coerceIn(0, 3)])) {
            val cover = media.firstOrNull { it.key == category.coverKey } ?: media.firstOrNull()
            if (cover != null) AsyncImage(cover.uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text(category.symbol, Modifier.align(Alignment.Center), style = MaterialTheme.typography.displaySmall, color = Color(0xFF354C3D))
            if (category.pinned) Icon(Icons.Outlined.PushPin, "Pinned", Modifier.align(Alignment.TopStart).padding(12.dp).size(18.dp), tint = Color.White)
            IconButton(onClick = onEdit, Modifier.align(Alignment.TopEnd)) { Icon(Icons.Outlined.MoreHoriz, "Edit ${category.name}", tint = if (cover == null) Color(0xFF354C3D) else Color.White) }
        }
        Column(Modifier.padding(14.dp)) { Text("${category.symbol} ${category.name}", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("${media.size} accessible items", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
@Composable fun AlbumsScreen(media: List<MediaItem>, categories: List<Category>, memberships: Map<Long, Set<String>>, favorites: Set<String>, onCreate: () -> Unit, onEdit: (Category) -> Unit, onFilter: (String, Long?, String?) -> Unit, onVault: () -> Unit) {
    val prefs = LocalPrismPreferences.current
    val ordered = remember(categories, prefs) { categories.filter { prefs.showHidden || !it.hidden }.let { if (prefs.categorySort == "Name") it.sortedBy { c -> c.name.lowercase() } else if (prefs.categorySort == "Newest") it.sortedByDescending { c -> c.id } else it } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 125.dp)) {
        GlassToolbar("Albums", "A little order. Entirely your own.")
        Column(Modifier.padding(horizontal = 22.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Your categories", style = MaterialTheme.typography.headlineSmall); FilledTonalIconButton(onCreate) { Icon(Icons.Outlined.Add, "New category") } }
            Text("Virtual collections · originals stay in place", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            if (ordered.isEmpty()) {
                Column(Modifier.fillMaxWidth().clip(SoftShape).background(MaterialTheme.colorScheme.surface).padding(24.dp)) { Text("Make room for your favorites.", style = MaterialTheme.typography.titleLarge); Spacer(Modifier.height(8.dp)); Text("Family, reaction pictures, work, or a category only you could name.", style = MaterialTheme.typography.bodyMedium); Spacer(Modifier.height(14.dp)); GlassButton("New Category", Icons.Outlined.Add, onClick = onCreate) }
            }
            ordered.chunked(2).forEach { row -> Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) { row.forEach { c -> Box(Modifier.weight(1f)) { GlassAlbumCard(c, media.filter { it.key in memberships[c.id].orEmpty() }, { onFilter(c.name, c.id, null) }, { onEdit(c) }) } }; if (row.size == 1) Spacer(Modifier.weight(1f)) } }
            if (ordered.isNotEmpty()) TextButton(onCreate) { Icon(Icons.Outlined.Add, null); Text(" New Category") }
            SectionLabel("Smart collections", "Based on local metadata")
            listOf(Triple("Favorites", Icons.Outlined.FavoriteBorder, media.count { it.key in favorites }), Triple("Videos", Icons.Outlined.PlayCircleOutline, media.count { it.isVideo }), Triple("Screenshots", Icons.Outlined.Screenshot, media.count { "screenshot" in (it.name + it.folder).lowercase() }), Triple("Recent", Icons.Outlined.History, media.count { it.added > System.currentTimeMillis() - 7L * 86400_000 })).forEach { (name, icon, count) ->
                ListItem(headlineContent = { Text(name) }, leadingContent = { Icon(icon, null) }, trailingContent = { Row(verticalAlignment = Alignment.CenterVertically) { Text("$count"); Spacer(Modifier.width(8.dp)); Icon(Icons.Outlined.ChevronRight, null) } }, modifier = Modifier.clip(RoundedCornerShape(18.dp)).clickable { onFilter(name, null, null) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
            }
            SectionLabel("Device folders", "Actual storage locations")
            media.groupBy { it.folder }.toSortedMap().forEach { (folder, files) -> ListItem(headlineContent = { Text(folder.ifBlank { "Unknown folder" }, maxLines = 1, overflow = TextOverflow.Ellipsis) }, supportingContent = { Text("${files.size} items") }, leadingContent = { Icon(Icons.Outlined.FolderOpen, null) }, modifier = Modifier.clip(RoundedCornerShape(18.dp)).clickable { onFilter(folder, null, folder) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent)) }
            HorizontalDivider(Modifier.padding(vertical = 20.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = .2f))
            Row(Modifier.fillMaxWidth().clip(SoftShape).background(MaterialTheme.colorScheme.primary).clickable(onClick = onVault).padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.onPrimary); Spacer(Modifier.width(15.dp)); Column(Modifier.weight(1f)) { Text("Private Vault", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary); Text("Encrypted on this device", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .8f)) }; Icon(Icons.Outlined.ChevronRight, "Open vault", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}
@Composable fun CategoryEditor(category: Category?, keys: List<String>, media: List<MediaItem>, onDismiss: () -> Unit, onSave: (Category) -> Unit, onDelete: (Category) -> Unit, onOrder: (Category, Int) -> Unit) {
    var name by remember(category) { mutableStateOf(category?.name.orEmpty()) }; var symbol by remember(category) { mutableStateOf(category?.symbol ?: "◇") }; var accent by remember(category) { mutableIntStateOf(category?.accent ?: 0) }
    var pinned by remember(category) { mutableStateOf(category?.pinned ?: false) }; var hidden by remember(category) { mutableStateOf(category?.hidden ?: false) }; var cover by remember(category) { mutableStateOf(category?.coverKey) }
    var confirmDelete by remember { mutableStateOf(false) }
    GlassDialog(if (category == null) "New category" else "Edit category", onDismiss, confirm = { TextButton({ onSave((category ?: Category(name = "")).copy(name = name, symbol = symbol, accent = accent, pinned = pinned, hidden = hidden, coverKey = cover)); onDismiss() }, enabled = name.isNotBlank()) { Text("Save") } }) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Categories organize your library without copying or deleting photos.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(name, { if (it.length <= 80) name = it }, label = { Text("Category name") }, singleLine = true, shape = RoundedCornerShape(16.dp))
            GlassSegmentedControl(listOf("◇", "♡", "☀", "✿", "☺", "☆"), symbol) { symbol = it }
            Text("Cover accent", style = MaterialTheme.typography.labelMedium)
            GlassSegmentedControl(listOf("Sage", "Sand", "Mist", "Rose"), listOf("Sage", "Sand", "Mist", "Rose")[accent]) { accent = listOf("Sage", "Sand", "Mist", "Rose").indexOf(it) }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Pin to top", Modifier.weight(1f)); Switch(pinned, { pinned = it }) }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Hide from Albums", Modifier.weight(1f)); Switch(hidden, { hidden = it }) }
            val covers = media.filter { it.key in keys }.take(24)
            if (covers.isNotEmpty()) { Text("Choose cover", style = MaterialTheme.typography.labelMedium); Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { covers.forEach { item -> AsyncImage(item.uri, "Use ${item.name} as cover", Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)).border(if (cover == item.key) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)).clickable { cover = item.key }, contentScale = ContentScale.Crop) } } }
            if (category != null) { Row { TextButton({ onOrder(category, -1); onDismiss() }) { Text("Move earlier") }; TextButton({ onOrder(category, 1); onDismiss() }) { Text("Move later") } }; TextButton({ confirmDelete = true }) { Text("Delete category", color = MaterialTheme.colorScheme.error) } }
        }
    }
    if (confirmDelete && category != null) GlassDialog("Delete ${category.name}?", { confirmDelete = false }, { TextButton({ onDelete(category); onDismiss() }) { Text("Delete category") } }) { Text("Only this virtual category and its memberships are removed. Original media is kept.") }
}
@Composable fun CategoryPicker(categories: List<Category>, onDismiss: () -> Unit, onSelect: (Long) -> Unit, onCreate: () -> Unit) {
    GlassBottomSheet(onDismiss) {
        Text("Add to category", Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.headlineSmall)
        Text("An item can belong to several categories.", Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
        Column(Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState())) { categories.forEach { category -> ListItem(headlineContent = { Text("${category.symbol} ${category.name}") }, modifier = Modifier.clickable { onSelect(category.id); onDismiss() }, trailingContent = { Icon(Icons.Outlined.Add, null) }) } }
        TextButton(onCreate, Modifier.padding(horizontal = 16.dp)) { Icon(Icons.Outlined.Add, null); Text(" New Category") }
        Spacer(Modifier.height(24.dp))
    }
}
