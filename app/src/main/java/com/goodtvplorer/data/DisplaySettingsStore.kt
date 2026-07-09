package com.goodtvplorer.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.displayDataStore by preferencesDataStore("display_settings")

class DisplaySettingsStore(private val context: Context) {
    private val fontScaleKey = floatPreferencesKey("font_scale")

    val fontScale: Flow<Float> = context.displayDataStore.data.map { prefs ->
        (prefs[fontScaleKey] ?: 0.85f).coerceIn(0.75f, 1.15f)
    }

    suspend fun setFontScale(value: Float) {
        context.displayDataStore.edit { prefs -> prefs[fontScaleKey] = value.coerceIn(0.75f, 1.15f) }
    }
}
