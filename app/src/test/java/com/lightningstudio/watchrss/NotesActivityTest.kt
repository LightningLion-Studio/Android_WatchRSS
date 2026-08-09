package com.lightningstudio.watchrss

import org.junit.Assert.assertEquals
import org.junit.Test

class NotesActivityTest {
    @Test
    fun notePreview_doesNotRepeatTitle() {
        assertEquals(
            "第一段 第二段",
            notePreview("会议安排", "会议安排\n\n第一段\n第二段")
        )
    }

    @Test
    fun notePreview_keepsFirstLineWhenDifferentFromTitle() {
        assertEquals(
            "先买牛奶 再取快递",
            notePreview("待办", "先买牛奶\n再取快递")
        )
    }

    @Test
    fun notePreview_collapsesBlankLinesAndLimitsLength() {
        val preview = notePreview("标题", "\n\n" + "内容".repeat(100))

        assertEquals(120, preview.length)
    }
}
