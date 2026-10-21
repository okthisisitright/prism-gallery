package com.prism.gallery

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.*
import androidx.navigation.compose.*
import com.kyant.backdrop.backdrops.*
import com.prism.gallery.core.database.Category
import com.prism.gallery.core.designsystem.*
import com.prism.gallery.core.media.*
import com.prism.gallery.core.model.*
import com.prism.gallery.feature.albums.*
import com.prism.gallery.feature.explore.ExploreScreen
import com.prism.gallery.feature.photos.PhotoGrid
import com.prism.gallery.feature.settings.SettingsScreen
import com.prism.gallery.feature.vault.VaultScreen
import com.prism.gallery.feature.viewer.MediaViewer

private data class ViewerSession(val items: List<MediaItem>, val initial: Int)
private data class FolderSession(val title: String, val filter: LibraryFilter)
@Composable fun PrismApp(vm: LibraryViewModel, activity: MainActivity, deleteRequest: (List<MediaItem>, Boolean, (Boolean) -> Unit) -> Unit, biometric: (Boolean, LibraryViewModel) -> Unit) {
    val prefs by vm.preferences.collectAsStateWithLifecycle(); val media by vm.media.collectAsStateWithLifecycle(); val categories by vm.categories.collectAsStateWithLifecycle(); val memberships by vm.memberships.collectAsStateWithLifecycle(); val favorites by vm.favorites.collectAsStateWithLifecycle(); val access by vm.access.collectAsStateWithLifecycle(); val loading by vm.loading.collectAsStateWithLifecycle(); val error by vm.error.collectAsStateWithLifecycle(); val busy by vm.busy.collectAsStateWithLifecycle(); val summary by vm.importSummary.collectAsStateWithLifecycle(); val vaultUnlocked by vm.app.vault.unlocked.collectAsStateWithLifecycle()
    val context = LocalContext.current; val owner = LocalLifecycleOwner.current; val nav = rememberNavController(); val currentEntry by nav.currentBackStackEntryAsState(); val currentRoute = currentEntry?.destination?.route ?: "photos"
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }; var viewer by remember { mutableStateOf<ViewerSession?>(null) }; var folder by remember { mutableStateOf<FolderSession?>(null) }
    var vaultOpen by rememberSaveable { mutableStateOf(false) }; var vaultPicker by remember { mutableStateOf(false) }; var pickerKeys by remember { mutableStateOf(setOf<String>()) }; var pendingImport by remember { mutableStateOf<List<MediaItem>?>(null) }
    var categoryPicker by remember { mutableStateOf<List<String>?>(null) }; var editorOpen by remember { mutableStateOf(false) }; var editing by remember { mutableStateOf<Category?>(null) }; var editorKeys by remember { mutableStateOf<List<String>>(emptyList()) }
    var deleteItems by remember { mutableStateOf<List<MediaItem>?>(null) }; var copyItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }; var coverItem by remember { mutableStateOf<MediaItem?>(null) }
    var exploreQuery by rememberSaveable { mutableStateOf("") }; var exploreKind by rememberSaveable { mutableStateOf("All") }
    val snack = remember { SnackbarHostState() }; val backdrop = rememberLayerBackdrop(); val gridState = rememberLazyGridState()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refresh() }
    fun requestPermissions() { permission.launch(mediaPermissions()) }
    val tree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) { val pending = copyItems; copyItems = emptyList(); vm.launchAction { val count = copyToTree(context, uri, pending); vm.messages.send("Copied $count items. Originals kept.") } } else copyItems = emptyList() }
    fun copy(items: List<MediaItem>) { copyItems = items; tree.launch(null) }
    fun toggle(item: MediaItem) { selected = if (item.key in selected) selected - item.key else selected + item.key }
    fun open(item: MediaItem, items: List<MediaItem>) { viewer = ViewerSession(items, items.indexOfFirst { it.key == item.key }.coerceAtLeast(0)) }
    fun vaultImport(items: List<MediaItem>) { pendingImport = items; viewer = null; vaultOpen = true }
    LaunchedEffect(Unit) { for (message in vm.messages) snack.showSnackbar(message) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) { viewer = null; selected = emptyList(); vm.refresh() } }
        owner.lifecycle.addObserver(observer); onDispose { owner.lifecycle.removeObserver(observer) }
    }
    // FLAG_SECURE is applied before private UI is committed; it remains set for vault dialogs and picker.
    DisposableEffect(vaultOpen) { activity.secure(vaultOpen); onDispose { if (vaultOpen) activity.secure(false) } }
    LaunchedEffect(vaultUnlocked, pendingImport) { if (vaultUnlocked && pendingImport != null) { val items = pendingImport!!; pendingImport = null; vm.importToVault(items) } }
    fun closeVault() { vm.app.vault.lock(); pendingImport = null; vaultPicker = false; vaultOpen = false }
    BackHandler(enabled = viewer != null || vaultOpen || folder != null || selected.isNotEmpty()) {
        when { viewer != null -> viewer = null; vaultPicker -> { vaultPicker = false; pickerKeys = emptySet() }; vaultOpen -> closeVault(); selected.isNotEmpty() -> selected = emptyList(); else -> folder = null }
    }
    PrismTheme(prefs) {
        val lightBars = MaterialTheme.colorScheme.background.luminance() > .5f && viewer == null
        SideEffect { WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply { isAppearanceLightStatusBars = lightBars; isAppearanceLightNavigationBars = lightBars } }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                if (vaultOpen) {
                    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                        if (vaultPicker && vaultUnlocked) {
                            Column {
                                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) { IconButton({ vaultPicker = false; pickerKeys = emptySet() }) { Icon(Icons.Outlined.ArrowBack, "Back") }; Text("Choose images", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge); TextButton({ val items = media.filter { it.key in pickerKeys }; vaultPicker = false; pickerKeys = emptySet(); vm.importToVault(items) }, enabled = pickerKeys.isNotEmpty() && !busy) { Text("Import ${pickerKeys.size}") } }
                                Text("Encrypted copies are verified first. Public originals stay until you remove them.", Modifier.padding(horizontal = 24.dp, vertical = 10.dp), style = MaterialTheme.typography.bodySmall)
                                if (!access.any) Button({ requestPermissions() }, Modifier.padding(24.dp)) { Text("Allow media access") }
                                PhotoGrid(media.filter { !it.isVideo && it.size <= 32L * 1024 * 1024 }, prefs.columns, prefs.grouping, pickerKeys, emptySet(), { pickerKeys = if (it.key in pickerKeys) pickerKeys - it.key else pickerKeys + it.key }, { pickerKeys = pickerKeys + it.key })
                            }
                        } else VaultScreen(vm, ::closeVault, { biometric(it, vm) }, { vaultPicker = true })
                    }
                } else {
                    Box(Modifier.fillMaxSize().layerBackdrop(backdrop).statusBarsPadding()) {
                        if (folder != null) {
                            val session = folder!!; val items = remember(media, favorites, memberships, categories, session, prefs.oldest) { filterMedia(media, session.filter, favorites, memberships, categories.associate { it.id to it.name }, prefs.oldest) }
                            Column { GlassToolbar(session.title, "${items.size} accessible items") { IconButton({ folder = null; selected = emptyList() }) { Icon(Icons.Outlined.Close, "Close collection") } }; if (items.isEmpty()) EmptyState(Icons.Outlined.Collections, "A little space", "No accessible media in this collection. A hidden or unselected source may still exist on the device.") else PhotoGrid(items, prefs.columns, prefs.grouping, selected.toSet(), favorites, ::toggle, { open(it, items) }) }
                        } else NavHost(nav, startDestination = "photos") {
                            composable("photos") {
                                Column {
                                    GlassToolbar("Photos", "${media.size} moments · stored on this device") { IconButton({ nav.navigate("explore") { launchSingleTop = true } }) { Icon(Icons.Outlined.Search, "Search library") } }
                                    if (!access.any) {
                                        Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp).verticalScroll(rememberScrollState()).padding(bottom = 130.dp)) {
                                            Text("Your life,\nin a different light.", Modifier.padding(top = 35.dp, bottom = 20.dp), style = MaterialTheme.typography.displaySmall)
                                            Text("A quieter home for your photos. Beautifully organized. Entirely on your device.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Spacer(Modifier.height(35.dp)); Button({ requestPermissions() }, Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Text("Choose media access") }
                                            TextButton({ context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text("Open Android permission settings") }
                                            Text("Allow all media, or choose just the photos and videos you want Prism to see. You can change this any time.", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodySmall)
                                            Row(Modifier.padding(top = 34.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Shield, null, Modifier.size(18.dp)); Text("OFFLINE BY DESIGN", style = MaterialTheme.typography.labelSmall) }
                                        }
                                    } else {
                                        if (!(access.images && access.videos)) Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) { Text(access.label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall); TextButton({ requestPermissions() }) { Text("Manage") } }
                                        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                                        if (error != null) EmptyState(Icons.Outlined.ErrorOutline, "Library unavailable", error!!, { GlassButton("Rescan", onClick = { vm.refresh() }) })
                                        else if (media.isEmpty() && !loading) EmptyState(Icons.Outlined.PhotoLibrary, "The beginning of a collection", "No accessible images or videos were found. Choose more media or check Android permissions.", { GlassButton("Manage access", onClick = { requestPermissions() }) })
                                        else PhotoGrid(if (prefs.oldest) media.sortedBy { it.date } else media, prefs.columns, prefs.grouping, selected.toSet(), favorites, ::toggle, { open(it, if (prefs.oldest) media.sortedBy { it.date } else media) }, state = gridState)
                                    }
                                }
                            }
                            composable("albums") { AlbumsScreen(media, categories, memberships, favorites, { editing = null; editorKeys = emptyList(); editorOpen = true }, { editing = it; editorKeys = memberships[it.id].orEmpty().toList(); editorOpen = true }, { title, category, path -> folder = FolderSession(title, LibraryFilter(kind = if (category == null && path == null) title else "All", category = category, folder = path)) }, { vaultOpen = true }) }
                            composable("explore") { ExploreScreen(media, favorites, memberships, categories.associate { it.id to it.name }, exploreQuery, { exploreQuery = it }, exploreKind, { exploreKind = it }, selected.toSet(), ::toggle, ::open) }
                            composable("settings") { SettingsScreen(vm, prefs) { requestPermissions() } }
                        }
                    }
                    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
                        Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp).widthIn(max = 520.dp)) {
                            if (selected.isEmpty()) GlassTabBar(listOf(Triple("photos", "Photos", Icons.Outlined.PhotoLibrary), Triple("albums", "Albums", Icons.Outlined.CollectionsBookmark), Triple("explore", "Explore", Icons.Outlined.TravelExplore), Triple("settings", "Settings", Icons.Outlined.Tune)), currentRoute) { route -> folder = null; nav.navigate(route) { popUpTo(nav.graph.startDestinationId) { saveState = true }; launchSingleTop = true; restoreState = true } }
                            else GlassSelectionBar(selected.size, { selected = emptyList() }) {
                                val items = media.filter { it.key in selected }
                                IconButton({ vm.favorite(items) }) { Icon(Icons.Outlined.FavoriteBorder, "Favorite selected") }
                                IconButton({ categoryPicker = selected }) { Icon(Icons.Outlined.CreateNewFolder, "Add selected to category") }
                                IconButton({ vm.launchAction { shareMedia(context, items) } }) { Icon(Icons.Outlined.Share, "Share selected") }
                                IconButton({ vaultImport(items) }) { Icon(Icons.Outlined.Lock, "Import selected to vault") }
                                IconButton({ copy(items) }) { Icon(Icons.Outlined.FileCopy, "Copy selected") }
                                folder?.filter?.category?.let { id -> IconButton({ vm.removeFromCategory(id, selected); selected = emptyList() }) { Icon(Icons.Outlined.RemoveCircleOutline, "Remove from this category only") } }
                                IconButton({ deleteItems = items }) { Icon(Icons.Outlined.DeleteOutline, "Trash selected") }
                            }
                        }
                    }
                }
                viewer?.let { session ->
                    val assigned = media.associate { item -> item.key to categories.filter { item.key in memberships[it.id].orEmpty() }.map { it.name } }
                    MediaViewer(session.items, session.initial, favorites, assigned, { viewer = null }, { vm.favorite(listOf(it)) }, { categoryPicker = listOf(it.key) }, { vaultImport(listOf(it)) }, { vm.launchAction { shareMedia(context, listOf(it)) } }, { copy(listOf(it)) }, { deleteItems = listOf(it) }, { vm.launchAction { shareMedia(context, listOf(it), true) } }, { coverItem = it })
                }
                SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 110.dp, start = 12.dp, end = 12.dp))
                if (busy) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .5f)), contentAlignment = Alignment.Center) { Surface(shape = SoftShape) { Column(Modifier.padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(15.dp)) { CircularProgressIndicator(); Text("Encrypting & verifying…"); Text("Keep Prism open. Originals are safe.", style = MaterialTheme.typography.bodySmall) } } }
            }
        }
        categoryPicker?.let { keys -> CategoryPicker(categories, { categoryPicker = null }, { vm.addToCategory(it, keys) }, { editorKeys = keys; editing = null; categoryPicker = null; editorOpen = true }) }
        if (editorOpen) CategoryEditor(editing, editorKeys, media, { editorOpen = false }, { vm.saveCategory(it, if (editing == null) editorKeys else emptyList()) }, { vm.deleteCategory(it.id) }, vm::orderCategory)
        coverItem?.let { item -> CategoryPicker(categories.filter { item.key in memberships[it.id].orEmpty() }, { coverItem = null }, { id -> categories.firstOrNull { it.id == id }?.let { category -> vm.saveCategory(category.copy(coverKey = item.key)) }; coverItem = null }, { editorKeys = listOf(item.key); editing = null; coverItem = null; editorOpen = true }) }
        deleteItems?.let { items -> GlassDialog(if (Build.VERSION.SDK_INT >= 30) "Move ${items.size} items to trash?" else "Permanently delete?", { deleteItems = null }, { TextButton({ deleteItems = null; deleteRequest(items, false) { success -> vm.launchAction { vm.messages.send(if (success) "Android confirmed the removal" else "Removal canceled or failed. Originals may remain.") }; if (success) { selected = emptyList(); viewer = null }; vm.refresh() } }) { Text(if (Build.VERSION.SDK_INT >= 30) "Request trash" else "Request deletion") } }) { Text(if (Build.VERSION.SDK_INT >= 30) "Android will ask for confirmation. This affects original media in all galleries. Virtual categories do not keep copies." else "Android 8–10 deletion is permanent in this version. Delete one item at a time. This affects the original, not just a category.") } }
        summary?.let { result -> GlassDialog("Vault import result", { vm.importSummary.value = null }, {
            Row { TextButton({ vm.importSummary.value = null }) { Text("Keep originals") }; if (result.pairs.isNotEmpty()) TextButton({ vm.importSummary.value = null; deleteRequest(result.pairs.map { it.second }, true) { success -> vm.launchAction { if (success) result.pairs.forEach { vm.dao.vaultSource(it.first.id, false) }; vm.messages.send(if (success) "Android confirmed original removal. Other external copies may exist." else "Public originals STILL EXIST or could not be removed. Encrypted copies are safe.") }; vm.refresh() } }) { Text("Remove originals…") } }
        }) { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("${result.pairs.size} encrypted copies verified and committed."); Text("Plaintext originals STILL EXIST in the public library until Android confirms removal. Keeping them means the source photos are not private."); result.failures.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }; if (Build.VERSION.SDK_INT < 30 && result.pairs.size > 1) Text("On Android 8–10, keep originals now and delete them one at a time from Photos.", style = MaterialTheme.typography.bodySmall) } } }
    }
}
