package com.github.gbandszxc.goodtvplorer.ui.browser

import com.github.gbandszxc.goodtvplorer.data.FileKind
import com.github.gbandszxc.goodtvplorer.ui.theme.TvIcons
import kotlin.test.Test
import kotlin.test.assertSame

class BrowserScreenTest {
    @Test
    fun `each file kind uses its matching vector icon`() {
        val expected = mapOf(
            FileKind.Directory to TvIcons.Folder,
            FileKind.Image to TvIcons.Image,
            FileKind.Text to TvIcons.FileText,
            FileKind.Audio to TvIcons.Disc,
            FileKind.Video to TvIcons.Film,
            FileKind.Other to TvIcons.File,
        )

        expected.forEach { (kind, icon) ->
            assertSame(icon, fileKindIcon(kind), "FileKind.$kind 应使用对应的 TvIcons 图标")
        }
    }
}
