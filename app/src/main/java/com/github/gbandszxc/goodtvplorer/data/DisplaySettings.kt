package com.github.gbandszxc.goodtvplorer.data

import kotlin.math.roundToInt

internal const val AppFontScaleCalibration = 0.75f

// preference 是相对应用标准字号的比例，1f 在界面上显示为 100%。
internal fun effectiveFontScale(preference: Float) = AppFontScaleCalibration * preference.coerceIn(0.8f, 1.2f)
internal fun nextFontScale(value: Float, delta: Float) = (value + delta).coerceIn(0.8f, 1.2f)
internal fun fontScalePercent(value: Float) = (value * 100).roundToInt()
