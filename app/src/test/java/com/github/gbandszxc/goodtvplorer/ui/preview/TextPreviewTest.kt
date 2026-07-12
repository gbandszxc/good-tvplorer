package com.github.gbandszxc.goodtvplorer.ui.preview

import kotlin.test.Test
import kotlin.test.assertEquals

class TextPreviewTest {
    @Test
    fun `分页保留行号和空行`() {
        val pages = textPages("第一行\n\n第三行", linesPerPage = 2)

        assertEquals(listOf(NumberedLine(1, "第一行"), NumberedLine(2, "")), pages[0])
        assertEquals(listOf(NumberedLine(3, "第三行")), pages[1])
    }
}
