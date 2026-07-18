package com.github.gbandszxc.goodtvplorer.ui.browser

import com.github.gbandszxc.goodtvplorer.R
import com.github.gbandszxc.goodtvplorer.data.FileKind
import kotlin.test.Test
import kotlin.test.assertEquals

class BrowserScreenTest {
    @Test
    fun `each file kind uses its matching vector drawable`() {
        val expected = mapOf(
            FileKind.Directory to R.drawable.ic_folder,
            FileKind.Image to R.drawable.ic_image,
            FileKind.Text to R.drawable.ic_text,
            FileKind.Audio to R.drawable.ic_disc,
            FileKind.Video to R.drawable.ic_video,
            FileKind.Other to R.drawable.ic_file,
        )

        assertEquals(expected, FileKind.entries.associateWith(::fileKindIconRes))
    }
}
