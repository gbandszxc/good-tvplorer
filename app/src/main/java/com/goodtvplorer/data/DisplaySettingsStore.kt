package com.goodtvplorer.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

private val Context.displayDataStore by preferencesDataStore("display_settings")

internal const val BaseFontScale = 0.75f
internal fun effectiveFontScale(preference: Float) = BaseFontScale * preference.coerceIn(0.8f, 1.2f)
internal fun nextFontScale(value: Float, delta: Float) = (value + delta).coerceIn(0.8f, 1.2f)
internal fun fontScalePercent(value: Float) = (value * 100).roundToInt()

class DisplaySettingsStore(private val context: Context) {
    private val fontScaleKey = floatPreferencesKey("font_scale")

    val fontScale: Flow<Float> = context.displayDataStore.data.map { prefs ->
        (prefs[fontScaleKey] ?: 1f).coerceIn(0.8f, 1.2f)
    }

    suspend fun setFontScale(value: Float) {
        context.displayDataStore.edit { prefs -> prefs[fontScaleKey] = value.coerceIn(0.8f, 1.2f) }
    }
}
