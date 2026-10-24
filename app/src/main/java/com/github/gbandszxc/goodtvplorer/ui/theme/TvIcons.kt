package com.github.gbandszxc.goodtvplorer.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Lucide 风格图标集（24x24 viewport，stroke 2，round cap/join）。
 *
 * 颜色统一使用 [Color.Black] 占位，实际颜色由 `Icon(tint = ...)` 覆盖，
 * 因此同一 [ImageVector] 可在任意色调下复用。图标在 [TvIcons] 首次访问时构建一次，
 * 之后是稳定的不可变实例，不会在重组中重建。
 */
private class TvIconPath(val data: String, val filled: Boolean = false)

private fun stroke(data: String) = TvIconPath(data)

private fun filled(data: String) = TvIconPath(data, filled = true)

/**
 * 等价于 Compose 新版 `Builder.path(pathData: String, ...)` 重载：
 * 当前依赖的 Compose 版本只提供 `addPath(List<PathNode>)`，
 * 因此先用 [addPathNodes] 解析 SVG 路径字符串。
 */
private fun ImageVector.Builder.addStrokePath(data: String) {
    addPath(
        pathData = addPathNodes(data),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
}

private fun ImageVector.Builder.addFilledPath(data: String) {
    addPath(
        pathData = addPathNodes(data),
        fill = SolidColor(Color.Black),
    )
}

private fun tvIcon(name: String, vararg paths: TvIconPath): ImageVector {
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
    paths.forEach { path ->
        if (path.filled) {
            builder.addFilledPath(path.data)
        } else {
            builder.addStrokePath(path.data)
        }
    }
    return builder.build()
}

object TvIcons {
    // ---- 播放控制 ----

    val Play: ImageVector by lazy {
        tvIcon("TvIcon.Play", filled("M6 3l14 9-14 9V3z"))
    }

    val Pause: ImageVector by lazy {
        tvIcon(
            "TvIcon.Pause",
            filled(
                "M7 4h2a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z" +
                    "M15 4h2a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1h-2a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z",
            ),
        )
    }

    val SkipBack: ImageVector by lazy {
        tvIcon(
            "TvIcon.SkipBack",
            filled("M19 20 9 12l10-8v16z"),
            stroke("M5 19V5"),
        )
    }

    val SkipForward: ImageVector by lazy {
        tvIcon(
            "TvIcon.SkipForward",
            filled("M5 4l10 8-10 8V4z"),
            stroke("M19 5v14"),
        )
    }

    val Rewind10: ImageVector by lazy {
        tvIcon(
            "TvIcon.Rewind10",
            stroke("M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"),
            stroke("M3 3v5h5"),
        )
    }

    val Forward10: ImageVector by lazy {
        tvIcon(
            "TvIcon.Forward10",
            stroke("M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8"),
            stroke("M21 3v5h-5"),
        )
    }

    val Timer: ImageVector by lazy {
        tvIcon(
            "TvIcon.Timer",
            stroke("M10 2h4"),
            stroke("M12 14l3-3"),
            stroke("M12 6a8 8 0 1 0 0 16 8 8 0 0 0 0-16z"),
        )
    }

    val Gauge: ImageVector by lazy {
        tvIcon(
            "TvIcon.Gauge",
            stroke("M12 14l4-4"),
            stroke("M3.34 19a10 10 0 1 1 17.32 0"),
        )
    }

    val AspectRatio: ImageVector by lazy {
        tvIcon(
            "TvIcon.AspectRatio",
            stroke("M4 6h16a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z"),
        )
    }

    val Expand: ImageVector by lazy {
        tvIcon(
            "TvIcon.Expand",
            stroke("M8 3H5a2 2 0 0 0-2 2v3"),
            stroke("M21 8V5a2 2 0 0 0-2-2h-3"),
            stroke("M3 16v3a2 2 0 0 0 2 2h3"),
            stroke("M16 21h3a2 2 0 0 0 2-2v-3"),
        )
    }

    // ---- 导航与方向 ----

    val ArrowLeft: ImageVector by lazy {
        tvIcon(
            "TvIcon.ArrowLeft",
            stroke("M12 19l-7-7 7-7"),
            stroke("M19 12H5"),
        )
    }

    val ChevronLeft: ImageVector by lazy {
        tvIcon("TvIcon.ChevronLeft", stroke("m15 18-6-6 6-6"))
    }

    val ChevronRight: ImageVector by lazy {
        tvIcon("TvIcon.ChevronRight", stroke("m9 18 6-6-6-6"))
    }

    val ChevronUp: ImageVector by lazy {
        tvIcon("TvIcon.ChevronUp", stroke("m18 15-6-6-6 6"))
    }

    val ChevronDown: ImageVector by lazy {
        tvIcon("TvIcon.ChevronDown", stroke("m6 9 6 6 6-6"))
    }

    // ---- 搜索与排序 ----

    val Search: ImageVector by lazy {
        tvIcon(
            "TvIcon.Search",
            stroke("M11 3a8 8 0 1 0 0 16 8 8 0 0 0 0-16z"),
            stroke("m21 21-4.3-4.3"),
        )
    }

    val ArrowUpDown: ImageVector by lazy {
        tvIcon(
            "TvIcon.ArrowUpDown",
            stroke("m21 16-4 4-4-4"),
            stroke("M17 20V4"),
            stroke("m3 8 4-4 4 4"),
            stroke("M7 4v16"),
        )
    }

    // ---- 编辑与刷新 ----

    val Pencil: ImageVector by lazy {
        tvIcon(
            "TvIcon.Pencil",
            stroke("M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"),
            stroke("m15 5 4 4"),
        )
    }

    val RefreshCw: ImageVector by lazy {
        tvIcon(
            "TvIcon.RefreshCw",
            stroke("M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8"),
            stroke("M21 3v5h-5"),
            stroke("M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16"),
            stroke("M8 16H3v5"),
        )
    }

    // ---- 文件与媒体类型 ----

    val Folder: ImageVector by lazy {
        tvIcon(
            "TvIcon.Folder",
            stroke(
                "M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z",
            ),
        )
    }

    val Image: ImageVector by lazy {
        tvIcon(
            "TvIcon.Image",
            stroke("M4 3h16a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z"),
            stroke("M9 9a2 2 0 1 0 0 4 2 2 0 0 0 0-4z"),
            stroke("m21 15-3.09-3.09a2 2 0 0 0-2.82 0L6 21"),
        )
    }

    val FileText: ImageVector by lazy {
        tvIcon(
            "TvIcon.FileText",
            stroke("M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z"),
            stroke("M14 2v4a2 2 0 0 0 2 2h4"),
            stroke("M10 9H8"),
            stroke("M16 13H8"),
            stroke("M16 17H8"),
        )
    }

    val File: ImageVector by lazy {
        tvIcon(
            "TvIcon.File",
            stroke("M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z"),
            stroke("M14 2v4a2 2 0 0 0 2 2h4"),
        )
    }

    val Music: ImageVector by lazy {
        tvIcon(
            "TvIcon.Music",
            stroke("M9 18V5l12-2v13"),
            stroke("M9 18a3 3 0 1 1-6 0 3 3 0 0 1 6 0z"),
            stroke("M21 16a3 3 0 1 1-6 0 3 3 0 0 1 6 0z"),
        )
    }

    val Disc: ImageVector by lazy {
        tvIcon(
            "TvIcon.Disc",
            stroke("M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20z"),
            stroke("M12 10a2 2 0 1 0 0 4 2 2 0 0 0 0-4z"),
        )
    }

    val Film: ImageVector by lazy {
        tvIcon(
            "TvIcon.Film",
            stroke("M2 6h14a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H2a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z"),
            stroke("m16 13 5.223 3.482a.5.5 0 0 0 .777-.416V7.87a.5.5 0 0 0-.752-.432L16 10.5"),
        )
    }

    // ---- 视图切换 ----

    val Grid: ImageVector by lazy {
        tvIcon(
            "TvIcon.Grid",
            stroke("M4 3h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z"),
            stroke("M15 3h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1h-5a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z"),
            stroke("M15 14h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1h-5a1 1 0 0 1-1-1v-5a1 1 0 0 1 1-1z"),
            stroke("M4 14h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-5a1 1 0 0 1 1-1z"),
        )
    }

    val List: ImageVector by lazy {
        tvIcon(
            "TvIcon.List",
            stroke("M8 6h13"),
            stroke("M8 12h13"),
            stroke("M8 18h13"),
            stroke("M3 6h.01"),
            stroke("M3 12h.01"),
            stroke("M3 18h.01"),
        )
    }

    val Link: ImageVector by lazy {
        tvIcon(
            "TvIcon.Link",
            stroke("M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"),
            stroke("M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"),
        )
    }

    // ---- 系统与存储 ----

    val Settings: ImageVector by lazy {
        tvIcon(
            "TvIcon.Settings",
            stroke(
                "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08" +
                    "a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51" +
                    "a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08" +
                    "a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18" +
                    "a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39" +
                    "a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09" +
                    "a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25" +
                    "a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z",
            ),
            stroke("M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6z"),
        )
    }

    val HardDrive: ImageVector by lazy {
        tvIcon(
            "TvIcon.HardDrive",
            stroke("M22 12H2"),
            stroke(
                "M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89" +
                    "A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z",
            ),
            stroke("M6 16h.01"),
            stroke("M10 16h.01"),
        )
    }

    val Server: ImageVector by lazy {
        tvIcon(
            "TvIcon.Server",
            stroke("M4 2h16a2 2 0 0 1 2 2v4a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2z"),
            stroke("M4 14h16a2 2 0 0 1 2 2v4a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2v-4a2 2 0 0 1 2-2z"),
            stroke("M6 6h.01"),
            stroke("M6 18h.01"),
        )
    }

    val Monitor: ImageVector by lazy {
        tvIcon(
            "TvIcon.Monitor",
            stroke("M4 3h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z"),
            stroke("M8 21h8"),
            stroke("M12 17v4"),
        )
    }

    val Database: ImageVector by lazy {
        tvIcon(
            "TvIcon.Database",
            stroke("M12 2a9 3 0 1 0 0 6 9 3 0 0 0 0-6z"),
            stroke("M3 5v14a9 3 0 0 0 18 0V5"),
            stroke("M3 12a9 3 0 0 0 18 0"),
        )
    }

    val FolderUp: ImageVector by lazy {
        tvIcon(
            "TvIcon.FolderUp",
            stroke(
                "M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z",
            ),
            stroke("M12 10v6"),
            stroke("m9 13 3-3 3 3"),
        )
    }

    // ---- 通用动作 ----

    val Plus: ImageVector by lazy {
        tvIcon(
            "TvIcon.Plus",
            stroke("M5 12h14"),
            stroke("M12 5v14"),
        )
    }

    val Minus: ImageVector by lazy {
        tvIcon("TvIcon.Minus", stroke("M5 12h14"))
    }

    val Trash2: ImageVector by lazy {
        tvIcon(
            "TvIcon.Trash2",
            stroke("M3 6h18"),
            stroke("M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6"),
            stroke("M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"),
            stroke("M10 11v6"),
            stroke("M14 11v6"),
        )
    }

    val Check: ImageVector by lazy {
        tvIcon("TvIcon.Check", stroke("M20 6 9 17l-5-5"))
    }

    val X: ImageVector by lazy {
        tvIcon(
            "TvIcon.X",
            stroke("M18 6 6 18"),
            stroke("m6 6 12 12"),
        )
    }

    val Save: ImageVector by lazy {
        tvIcon(
            "TvIcon.Save",
            stroke(
                "M15.2 3a2 2 0 0 1 1.4.6l3.8 3.8a2 2 0 0 1 .6 1.4V19a2 2 0 0 1-2 2H5" +
                    "a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
            ),
            stroke("M17 21v-7a1 1 0 0 0-1-1H8a1 1 0 0 0-1 1v7"),
            stroke("M7 3v4a1 1 0 0 0 1 1h7"),
        )
    }

    val Info: ImageVector by lazy {
        tvIcon(
            "TvIcon.Info",
            stroke("M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20z"),
            stroke("M12 16v-4"),
            stroke("M12 8h.01"),
        )
    }

    val Github: ImageVector by lazy {
        tvIcon(
            "TvIcon.Github",
            stroke(
                "M15 22v-4a4.8 4.8 0 0 0-1-3.5c3 0 6-2 6-5.5.08-1.25-.27-2.48-1-3.5" +
                    ".28-1.15.28-2.35 0-3.5 0 0-1 0-3 1.5-2.64-.5-5.36-.5-8 0C6 2 5 2 5 2" +
                    "c-.3 1.15-.3 2.35 0 3.5A5.403 5.403 0 0 0 4 9c0 3.5 3 5.5 6 5.5" +
                    "-.39.49-.68 1.05-.85 1.65-.17.6-.22 1.23-.15 1.85v4",
            ),
            stroke("M9 18c-4.51 2-5-2-7-2"),
        )
    }

    val WrapText: ImageVector by lazy {
        tvIcon(
            "TvIcon.WrapText",
            stroke("M3 6h18"),
            stroke("M3 12h15a3 3 0 1 1 0 6h-4"),
            stroke("m16 16-2 2 2 2"),
            stroke("M3 18h7"),
        )
    }

    val Repeat: ImageVector by lazy {
        tvIcon(
            "TvIcon.Repeat",
            stroke("m17 2 4 4-4 4"),
            stroke("M3 11v-1a4 4 0 0 1 4-4h14"),
            stroke("m7 22-4-4 4-4"),
            stroke("M21 13v1a4 4 0 0 1-4 4H3"),
        )
    }

    val RepeatOne: ImageVector by lazy {
        tvIcon(
            "TvIcon.RepeatOne",
            stroke("m17 2 4 4-4 4"),
            stroke("M3 11v-1a4 4 0 0 1 4-4h14"),
            stroke("m7 22-4-4 4-4"),
            stroke("M21 13v1a4 4 0 0 1-4 4H3"),
            stroke("M11 10h1v4"),
        )
    }

    val Shuffle: ImageVector by lazy {
        tvIcon(
            "TvIcon.Shuffle",
            stroke("m18 14 4 4-4 4"),
            stroke("m18 2 4 4-4 4"),
            stroke("M2 18h1.973a4 4 0 0 0 3.3-1.7l5.454-8.6a4 4 0 0 1 3.3-1.7H22"),
            stroke("M2 6h1.972a4 4 0 0 1 3.6 2.2"),
            stroke("M22 18h-6.041a4 4 0 0 1-3.3-1.8l-.359-.45"),
        )
    }

    val AlertTriangle: ImageVector by lazy {
        tvIcon(
            "TvIcon.AlertTriangle",
            stroke("m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3z"),
            stroke("M12 9v4"),
            stroke("M12 17h.01"),
        )
    }

    val Eye: ImageVector by lazy {
        tvIcon(
            "TvIcon.Eye",
            stroke("M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7z"),
            stroke("M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6z"),
        )
    }
}
