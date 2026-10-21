package com.prism.gallery.feature.photos

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.prism.gallery.core.designsystem.*
import com.prism.gallery.core.model.*

@OptIn(ExperimentalFoundationApi::class)
@Composable fun PhotoGrid(items: List<MediaItem>, columns: Int, grouping: String, selected: Set<String>, favorites: Set<String>, onSelect: (MediaItem) -> Unit, onOpen: (MediaItem) -> Unit, modifier: Modifier = Modifier, state: LazyGridState = rememberLazyGridState()) {
    val haptic = LocalHapticFeedback.current; val preferences = LocalPrismPreferences.current
    val groups = remember(items, grouping) { if (grouping == "None") listOf("" to items) else items.groupBy { dateLabel(it.date, if (grouping == "Day") "EEE, d MMM yyyy" else "MMMM yyyy") }.toList() }
    LazyVerticalGrid(columns = GridCells.Fixed(columns), state = state, modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(start = 6.dp, end = 6.dp, bottom = if (selected.isEmpty()) 120.dp else 185.dp), verticalArrangement = Arrangement.spacedBy(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        groups.forEach { (date, group) ->
            if (date.isNotBlank()) item(key = "date:$date", span = { GridItemSpan(maxLineSpan) }) { Text(date, Modifier.padding(start = 18.dp, top = 20.dp, bottom = 11.dp), style = MaterialTheme.typography.titleMedium) }
            items(group, key = { it.key }, contentType = { "media" }) { item ->
                Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceVariant).combinedClickable(onClick = { if (selected.isEmpty()) onOpen(item) else onSelect(item) }, onLongClick = { if (preferences.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress); onSelect(item) })) {
                    AsyncImage(item.uri, item.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    if (item.isVideo) Row(Modifier.align(Alignment.BottomStart).background(Color.Black.copy(alpha = .65f), RoundedCornerShape(topEnd = 8.dp)).padding(5.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.PlayArrow, "Video", Modifier.size(14.dp), tint = Color.White); Text("${item.duration / 60000}:${((item.duration / 1000) % 60).toString().padStart(2, '0')}", color = Color.White, style = MaterialTheme.typography.labelSmall) }
                    if (item.key in favorites) Icon(Icons.Outlined.Favorite, "Favorite", Modifier.align(Alignment.BottomEnd).padding(6.dp).size(16.dp), tint = Color.White)
                    if (selected.isNotEmpty()) { Box(Modifier.fillMaxSize().background(if (item.key in selected) MaterialTheme.colorScheme.primary.copy(alpha = .25f) else Color.Transparent)); Icon(if (item.key in selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, if (item.key in selected) "Selected" else "Unselected", Modifier.align(Alignment.TopEnd).padding(7.dp).size(24.dp), tint = if (item.key in selected) Color(0xFFDBF3D4) else Color.White) }
                }
            }
        }
    }
}
