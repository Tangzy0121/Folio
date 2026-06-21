package com.folio.reader.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownEditActionsTest {

    @Test
    fun wrap_emptySelection_putsCaretBetween() {
        val r = MarkdownEditActions.wrap("ab", 1, 1, "**", "**")
        assertEquals("a****b", r.text)
        assertEquals(3, r.selStart)   // 紧跟第一个 ** 之后
        assertEquals(3, r.selEnd)
    }

    @Test
    fun wrap_withSelection_wrapsAndKeepsSelection() {
        val r = MarkdownEditActions.wrap("a bold c", 2, 6, "**", "**")
        assertEquals("a **bold** c", r.text)
        assertEquals(4, r.selStart)   // bold 起点右移 2
        assertEquals(8, r.selEnd)
    }

    @Test
    fun linePrefix_singleLine() {
        val r = MarkdownEditActions.linePrefix("title", 0, 0, "# ")
        assertEquals("# title", r.text)
        assertEquals(0, r.selStart)
        assertEquals("# title".length, r.selEnd)
    }

    @Test
    fun linePrefix_multiLine_prefixesEachLine() {
        val text = "one\ntwo\nthree"
        // 选区跨第 1、2 行
        val r = MarkdownEditActions.linePrefix(text, 0, 5, "- ")
        assertEquals("- one\n- two\nthree", r.text)
    }

    @Test
    fun linePrefix_caretMidSecondLine_onlyThatLine() {
        val text = "one\ntwo\nthree"
        val r = MarkdownEditActions.linePrefix(text, 5, 5, "> ")
        assertEquals("one\n> two\nthree", r.text)
    }

    @Test
    fun insertBlock_replacesSelection_caretAtEnd() {
        val r = MarkdownEditActions.insertBlock("ab", 2, 2, "\n---\n")
        assertEquals("ab\n---\n", r.text)
        assertEquals("ab\n---\n".length, r.selStart)
        assertEquals(r.selStart, r.selEnd)
    }
}
