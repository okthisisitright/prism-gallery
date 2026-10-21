package com.prism.gallery.feature.settings

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.preferences by preferencesDataStore("preferences")
data class Preferences(val theme: String = "System", val glass: String = "Auto", val columns: Int = 3, val oldest: Boolean = false, val grouping: String = "Month", val haptics: Boolean = true, val motion: Boolean = true, val showHidden: Boolean = false, val categorySort: String = "Custom", val vaultTimeout: Int = 60)
class SettingsRepository(context: Context) {
    private val store = context.preferences
    val flow = store.data.map { p -> Preferences(p[stringPreferencesKey("theme")] ?: "System", p[stringPreferencesKey("glass")] ?: "Auto", p[intPreferencesKey("columns")] ?: 3, p[booleanPreferencesKey("oldest")] ?: false, p[stringPreferencesKey("grouping")] ?: "Month", p[booleanPreferencesKey("haptics")] ?: true, p[booleanPreferencesKey("motion")] ?: true, p[booleanPreferencesKey("hidden")] ?: false, p[stringPreferencesKey("categorySort")] ?: "Custom", p[intPreferencesKey("vaultTimeout")] ?: 60) }
    suspend fun string(key: String, value: String) { store.edit { it[stringPreferencesKey(key)] = value } }
    suspend fun boolean(key: String, value: Boolean) { store.edit { it[booleanPreferencesKey(key)] = value } }
    suspend fun int(key: String, value: Int) { store.edit { it[intPreferencesKey(key)] = value } }
}
