package com.prism.gallery

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.prism.gallery.core.database.*
import com.prism.gallery.core.model.*
import com.prism.gallery.feature.settings.Preferences
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

data class ImportSummary(val pairs: List<Pair<VaultRecord, MediaItem>>, val failures: List<String>)
class LibraryViewModel(val app: PrismApplication) : AndroidViewModel(app) {
    val dao = app.database.dao()
    val media = MutableStateFlow<List<MediaItem>>(emptyList())
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val access = MutableStateFlow(app.media.access())
    val categories = dao.categories().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val memberships = dao.memberships().map { rows -> rows.groupBy { it.categoryId }.mapValues { it.value.map { row -> row.mediaKey }.toSet() } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val favorites = dao.favorites().map { it.map { row -> row.mediaKey }.toSet() }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val preferences = app.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, Preferences())
    val vaultRecords = dao.vault().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val busy = MutableStateFlow(false)
    val importSummary = MutableStateFlow<ImportSummary?>(null)
    val messages = Channel<String>(Channel.BUFFERED)
    private var scan: Job? = null
    init {
        refresh()
        viewModelScope.launch { app.media.changes().collect { delay(300); refresh() } }
        launchAction { app.vault.cleanInterruptedImports() }
    }
    fun refresh() {
        scan?.cancel()
        access.value = app.media.access()
        // Clear stale references before requery; permission may have changed while backgrounded.
        media.value = emptyList()
        scan = viewModelScope.launch {
            loading.value = true; error.value = null
            try { media.value = app.media.load { media.value = it }; access.value = app.media.access() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { media.value = emptyList(); error.value = "Could not read the library. Check permissions or rescan. ${e.localizedMessage.orEmpty()}" }
            finally { loading.value = false }
        }
    }
    fun launchAction(action: suspend () -> Unit) { viewModelScope.launch { try { action() } catch (e: CancellationException) { throw e } catch (e: Exception) { messages.send(e.localizedMessage ?: "The operation could not be completed") } } }
    fun favorite(items: List<MediaItem>) = launchAction { val remove = items.all { it.key in favorites.value }; items.forEach { if (remove) dao.unfavorite(it.key) else dao.favorite(Favorite(it.key)) } }
    fun saveCategory(category: Category, keys: List<String> = emptyList()) = launchAction {
        val name = category.name.trim(); require(name.isNotBlank()) { "Name your category" }
        app.database.withTransaction {
            val id = if (category.id == 0L) dao.create(category.copy(name = name, position = categories.value.size)) else { dao.update(category.copy(name = name)); category.id }
            if (keys.isNotEmpty()) dao.categorize(id, keys)
        }
        messages.send("Category saved. Originals stay in place.")
    }
    fun addToCategory(id: Long, keys: List<String>) = launchAction { dao.categorize(id, keys); messages.send("Added to category") }
    fun removeFromCategory(id: Long, keys: List<String>) = launchAction { dao.removeMembers(id, keys); messages.send("Removed from category. Originals kept.") }
    fun deleteCategory(id: Long) = launchAction { dao.deleteCategory(id); messages.send("Category deleted. Original media kept.") }
    fun orderCategory(category: Category, direction: Int) = launchAction {
        val ordered = categories.value.sortedBy { it.position }; val index = ordered.indexOfFirst { it.id == category.id }; val target = index + direction
        if (target in ordered.indices) { val updated = ordered.toMutableList(); java.util.Collections.swap(updated, index, target); app.database.withTransaction { updated.forEachIndexed { i, c -> dao.update(c.copy(position = i)) } } }
    }
    fun importToVault(items: List<MediaItem>) = launchAction {
        check(app.vault.unlocked.value) { "Unlock the Private Vault in Albums before importing" }
        busy.value = true
        try {
            val success = mutableListOf<Pair<VaultRecord, MediaItem>>(); val failed = mutableListOf<String>()
            items.forEach { item ->
                try { success += app.vault.import(item) to item }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { failed += "${item.name}: ${e.localizedMessage}" }
            }
            importSummary.value = ImportSummary(success, failed)
        } finally { busy.value = false }
    }
}
