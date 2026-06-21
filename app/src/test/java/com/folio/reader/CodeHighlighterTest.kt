package com.folio.reader

import com.folio.reader.reader.CodeHighlighter
import com.folio.reader.reader.CodeHighlighter.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** CodeHighlighter.tokenize 纯逻辑单测(JVM host,无需设备/android.text)。 */
class CodeHighlighterTest {

    private fun List<CodeHighlighter.Token>.text(src: String, kind: Kind) =
        filter { it.kind == kind }.map { src.substring(it.start, it.end) }

    @Test
    fun pythonKeywordsStringsComments() {
        val src = "def f(x):\n    return \"hi\"  # done"
        val t = CodeHighlighter.tokenize(src, "python")
        assertTrue("def 应为关键字", t.text(src, Kind.KEYWORD).contains("def"))
        assertTrue("return 应为关键字", t.text(src, Kind.KEYWORD).contains("return"))
        assertTrue("字符串应被识别", t.text(src, Kind.STRING).contains("\"hi\""))
        assertTrue("# 行注释应被识别", t.text(src, Kind.COMMENT).any { it.startsWith("# done") })
    }

    @Test
    fun keywordInsideStringNotColored() {
        // 字符串里的 return 不应被当关键字
        val src = "x = \"return value\""
        val t = CodeHighlighter.tokenize(src, "python")
        assertEquals(emptyList<String>(), t.text(src, Kind.KEYWORD))
        assertTrue(t.text(src, Kind.STRING).contains("\"return value\""))
    }

    @Test
    fun keywordInsideCommentNotColored() {
        val src = "// const here\nlet a = 1"
        val t = CodeHighlighter.tokenize(src, "js")
        // 注释里的 const 不算关键字;注释外的 let 算
        assertEquals(listOf("let"), t.text(src, Kind.KEYWORD))
        assertTrue(t.text(src, Kind.COMMENT).any { it.startsWith("// const here") })
    }

    @Test
    fun sqlCaseInsensitive() {
        val src = "select id from users where id > 3 -- note"
        val t = CodeHighlighter.tokenize(src, "sql")
        val kws = t.text(src, Kind.KEYWORD)
        assertTrue("小写 select 也应命中", kws.contains("select"))
        assertTrue(kws.contains("from"))
        assertTrue(kws.contains("where"))
        assertTrue("-- 注释", t.text(src, Kind.COMMENT).any { it.startsWith("-- note") })
        assertTrue("数字 3", t.text(src, Kind.NUMBER).contains("3"))
    }

    @Test
    fun blockCommentAndNumbers() {
        val src = "int x = 0xFF; /* hex\nspans lines */ int y = 42;"
        val t = CodeHighlighter.tokenize(src, "cpp")
        assertTrue(t.text(src, Kind.COMMENT).any { it.contains("hex") && it.contains("spans") })
        assertTrue(t.text(src, Kind.NUMBER).contains("0xFF"))
        assertTrue(t.text(src, Kind.NUMBER).contains("42"))
        assertTrue(t.text(src, Kind.KEYWORD).contains("int"))
    }

    @Test
    fun unterminatedStringDoesNotCrash() {
        val src = "s = \"oops no close\nnext line"
        val t = CodeHighlighter.tokenize(src, "python")
        // 不抛异常即可;字符串在行尾截断
        assertTrue(t.any { it.kind == Kind.STRING })
    }

    @Test
    fun functionCallAndOperatorHighlighted() {
        val src = "foo(1) + bar()"
        val t = CodeHighlighter.tokenize(src, "js")
        assertTrue("foo 应为函数", t.text(src, Kind.FUNCTION).contains("foo"))
        assertTrue("bar 应为函数", t.text(src, Kind.FUNCTION).contains("bar"))
        assertTrue("+ 应为运算符", t.text(src, Kind.OPERATOR).contains("+"))
    }

    @Test
    fun keywordBeforeParenStaysKeyword() {
        // if 后跟 ( 仍是关键字,不能被误判为函数
        val src = "if (x) { y() }"
        val t = CodeHighlighter.tokenize(src, "js")
        assertTrue("if 是关键字", t.text(src, Kind.KEYWORD).contains("if"))
        assertTrue("if 不应是函数", !t.text(src, Kind.FUNCTION).contains("if"))
        assertTrue("y 是函数", t.text(src, Kind.FUNCTION).contains("y"))
    }
}
