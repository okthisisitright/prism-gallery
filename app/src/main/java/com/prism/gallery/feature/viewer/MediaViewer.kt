package com.prism.gallery.feature.viewer

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.*
import coil3.compose.AsyncImage
import com.kyant.backdrop.backdrops.*
import com.prism.gallery.core.designsystem.*
import com.prism.gallery.core.model.*
import com.prism.gallery.core.model.MediaItem
import kotlinx.coroutines.delay
import kotlin.math.*

@Composable fun MediaViewer(items: List<MediaItem>, initial: Int, favorites: Set<String>, categories: Map<String, List<String>>, onDismiss: () -> Unit, onFavorite: (MediaItem) -> Unit, onCategory: (MediaItem) -> Unit, onVault: (MediaItem) -> Unit, onShare: (MediaItem) -> Unit, onCopy: (MediaItem) -> Unit, onDelete: (MediaItem) -> Unit, onOpenWith: (MediaItem) -> Unit, onCover: (MediaItem) -> Unit) {
    if (items.isEmpty()) { LaunchedEffect(Unit) { onDismiss() }; return }
    val pager = rememberPagerState(initialPage = initial.coerceIn(items.indices)) { items.size }
    var controls by remember { mutableStateOf(true) }; var zoomed by remember { mutableStateOf(false) }; var details by remember { mutableStateOf(false) }; var more by remember { mutableStateOf(false) }
    val item = items[pager.currentPage]; val backdrop = rememberLayerBackdrop()
    LaunchedEffect(pager.currentPage) { zoomed = false; controls = true }
    BackHandler { onDismiss() }
    Box(Modifier.fillMaxSize().background(Color(0xFF080B09))) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            HorizontalPager(pager, key = { items[it].key }, userScrollEnabled = !zoomed, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 0) { index ->
                val current = items[index]
                if (current.isVideo && index == pager.currentPage) VideoPlayer(current, Modifier.fillMaxSize(), controls)
                else ZoomPhoto(current, { if (index == pager.currentPage) zoomed = it }, { controls = !controls }, onDismiss)
            }
        }
        if (controls) CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
            PrismGlassSurface(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth()) {
                Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onDismiss) { Icon(Icons.Outlined.ArrowBack, "Back") }; Column(Modifier.weight(1f)) { Text(dateLabel(item.date, "d MMMM yyyy"), style = MaterialTheme.typography.titleMedium); Text("${pager.currentPage + 1} of ${items.size}", style = MaterialTheme.typography.bodySmall) }; IconButton({ details = true }) { Icon(Icons.Outlined.Info, "Details") } }
            }
            PrismGlassSurface(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp).fillMaxWidth()) {
                Row(Modifier.padding(8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    IconButton({ onFavorite(item) }) { Icon(if (item.key in favorites) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, "Favorite") }
                    IconButton({ onCategory(item) }) { Icon(Icons.Outlined.CreateNewFolder, "Add to category") }
                    IconButton({ onShare(item) }) { Icon(Icons.Outlined.Share, "Share") }
                    IconButton({ onDelete(item) }) { Icon(Icons.Outlined.DeleteOutline, "Trash or delete") }
                    IconButton({ more = true }) { Icon(Icons.Outlined.MoreHoriz, "More actions") }
                }
            }
        }
    }
    if (details) GlassDialog("Media details", { details = false }, { TextButton({ details = false }) { Text("Done") } }) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("Filename" to item.name, "Resolution" to "${item.width} × ${item.height}", "Size" to android.text.format.Formatter.formatFileSize(LocalContext.current, item.size), "Date" to dateLabel(item.date, "d MMMM yyyy · HH:mm"), "MIME type" to item.mime, "Folder" to item.folder, "Duration" to if (item.isVideo) "${item.duration / 1000} seconds" else "—", "Categories" to categories[item.key].orEmpty().joinToString().ifBlank { "None" }).forEach { (label, value) -> Column { Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary); Text(value, style = MaterialTheme.typography.bodyMedium) } }
        }
    }
    if (more) GlassBottomSheet({ more = false }) {
        listOf("Import image to Private Vault" to { onVault(item) }, "Copy to device folder…" to { onCopy(item) }, "Open with…" to { onOpenWith(item) }, "Use as category cover…" to { onCover(item) }).forEach { (label, action) -> ListItem(headlineContent = { Text(label) }, modifier = Modifier.clickable { more = false; action() }) }
        Spacer(Modifier.height(24.dp))
    }
}
@Composable private fun ZoomPhoto(item: MediaItem, onZoomed: (Boolean) -> Unit, onTap: () -> Unit, onDismiss: () -> Unit) {
    var scale by remember(item.key) { mutableFloatStateOf(1f) }; var pan by remember(item.key) { mutableStateOf(Offset.Zero) }; var viewport by remember { mutableStateOf(IntSize.Zero) }; var dismissOffset by remember { mutableFloatStateOf(0f) }
    fun bounded(value: Offset, zoom: Float): Offset {
        // Coil applies EXIF orientation; MediaStore bounds are used only to constrain pan, never to size/stretch the image.
        val fit = containSize(item.width.toFloat(), item.height.toFloat(), viewport.width.toFloat(), viewport.height.toFloat())
        val x = max(0f, (fit.first * zoom - viewport.width) / 2); val y = max(0f, (fit.second * zoom - viewport.height) / 2)
        return Offset(value.x.coerceIn(-x, x), value.y.coerceIn(-y, y))
    }
    Box(Modifier.fillMaxSize().onSizeChanged { viewport = it }
        .pointerInput(item.key, viewport) {
            detectTapGestures(onTap = { onTap() }, onDoubleTap = { point ->
                if (scale > 1.01f) { scale = 1f; pan = Offset.Zero; onZoomed(false) }
                else { scale = 2.5f; pan = bounded((Offset(viewport.width / 2f, viewport.height / 2f) - point) * 1.5f, scale); onZoomed(true) }
            })
        }
        .pointerInput(item.key, viewport) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var total = Offset.Zero; var vertical = false
                do {
                    val event = awaitPointerEvent()
                    val delta = event.calculatePan(); total += delta
                    if (event.changes.count { it.pressed } >= 2) {
                        val zoom = event.calculateZoom(); val next = (scale * zoom).coerceIn(1f, 6f)
                        val center = event.calculateCentroid(useCurrent = false) - Offset(viewport.width / 2f, viewport.height / 2f)
                        pan = bounded((pan - center) * (next / scale) + center + delta, next); scale = next
                        onZoomed(scale > 1.01f); event.changes.forEach { it.consume() }
                    } else if (scale > 1.01f) { pan = bounded(pan + delta, scale); event.changes.forEach { it.consume() } }
                    else if (vertical || (total.y > viewConfiguration.touchSlop && abs(total.y) > abs(total.x) * 1.4f)) { vertical = true; dismissOffset = total.y.coerceAtLeast(0f); event.changes.forEach { it.consume() } }
                } while (event.changes.any { it.pressed })
                if (vertical && dismissOffset > 150.dp.toPx()) onDismiss()
                dismissOffset = 0f
                if (scale <= 1.01f) { scale = 1f; pan = Offset.Zero; onZoomed(false) }
            }
        }) {
        AsyncImage(item.uri, item.name, Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = pan.x; translationY = pan.y + dismissOffset }, contentScale = ContentScale.Fit)
    }
}
@androidx.annotation.OptIn(UnstableApi::class)
@Composable private fun VideoPlayer(item: MediaItem, modifier: Modifier = Modifier, controls: Boolean) {
    val context = LocalContext.current; val owner = LocalLifecycleOwner.current
    val player = remember(item.uri) { ExoPlayer.Builder(context).build().apply {
        setAudioAttributes(AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).setUsage(C.USAGE_MEDIA).build(), true)
        setHandleAudioBecomingNoisy(true); setMediaItem(androidx.media3.common.MediaItem.fromUri(item.uri)); prepare()
    } }
    var playing by remember { mutableStateOf(false) }; var position by remember { mutableLongStateOf(0) }; var duration by remember { mutableLongStateOf(item.duration) }; var error by remember { mutableStateOf<String?>(null) }
    DisposableEffect(player, owner) {
        val listener = object : Player.Listener { override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }; override fun onPlayerError(e: PlaybackException) { error = "This video could not play (${e.errorCodeName}). Try Open with." } }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) player.pause() }
        player.addListener(listener); owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); player.removeListener(listener); player.release() }
    }
    LaunchedEffect(player) { while (true) { position = player.currentPosition; duration = player.duration.takeIf { it > 0 } ?: item.duration; delay(250) } }
    Box(modifier) {
        AndroidView(factory = { PlayerView(it).apply { this.player = player; useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT; layoutParams = ViewGroup.LayoutParams(-1, -1) } }, modifier = Modifier.fillMaxSize(), onRelease = { it.player = null })
        if (controls) Column(Modifier.align(Alignment.BottomCenter).padding(start = 24.dp, end = 24.dp, bottom = 132.dp).background(Color.Black.copy(alpha = .65f), SoftShape).padding(16.dp)) {
            if (error != null) Text(error!!, color = Color.White)
            Row(verticalAlignment = Alignment.CenterVertically) { IconButton({ if (player.isPlaying) player.pause() else { if (player.playbackState == Player.STATE_ENDED) player.seekTo(0); player.play() } }) { Icon(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (playing) "Pause" else "Play", tint = Color.White) }; Text("${position / 60000}:${((position / 1000) % 60).toString().padStart(2, '0')} / ${duration / 60000}:${((duration / 1000) % 60).toString().padStart(2, '0')}", color = Color.White, style = MaterialTheme.typography.bodySmall) }
            Slider(position.toFloat().coerceIn(0f, duration.toFloat().coerceAtLeast(1f)), { player.seekTo(it.toLong()); position = it.toLong() }, valueRange = 0f..duration.toFloat().coerceAtLeast(1f))
        }
    }
}
