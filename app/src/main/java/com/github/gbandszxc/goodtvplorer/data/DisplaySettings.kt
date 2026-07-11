package com.github.gbandszxc.goodtvplorer.data

import kotlin.math.roundToInt

internal const val BaseFontScale = 0.75f
internal fun effectiveFontScale(preference: Float) = BaseFontScale * preference.coerceIn(0.8f, 1.2f)
internal fun nextFontScale(value: Float, delta: Float) = (value + delta).coerceIn(0.8f, 1.2f)
internal fun fontScalePercent(value: Float) = (value * 100).roundToInt()
