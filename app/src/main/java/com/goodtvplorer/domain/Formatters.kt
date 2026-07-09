package com.goodtvplorer.domain

import java.text.DateFormat
import java.util.Date
import kotlin.math.log10
import kotlin.math.pow

object Formatters {
    fun size(bytes: Long?): String {
        if (bytes == null) return ""
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        val index = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(1, units.size)
        val value = bytes / 1024.0.pow(index.toDouble())
        return "%.1f %s".format(value, units[index - 1])
    }

    fun time(millis: Long?): String {
        if (millis == null || millis <= 0) return ""
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))
    }
}
