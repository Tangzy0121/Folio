package com.folio.reader.reader

/**
 * 格式工具栏背后的纯文本变换逻辑。无 Android 依赖,可在 JVM 上单测。
 *
 * 每个动作输入 (全文, 选区起, 选区止),返回新全文 + 新选区。
 * UI 层只负责把 [Result] 写回 EditText 并 setSelection。
 */
object MarkdownEditActions {

    data class Result(val text: String, val selStart: Int, val selEnd: Int)

    /** 包裹选区(加粗/斜体/删除线/行内码/链接)。无选区则插入定界符并把光标放中间。 */
    fun wrap(text: String, selStart: Int, selEnd: Int, before: String, after: String): Result {
        val s = selStart.coerceIn(0, text.length)
        val e = selEnd.coerceIn(s, text.length)
        val sel = text.substring(s, e)
        val newText = text.substring(0, s) + before + sel + after + text.substring(e)
        return if (sel.isEmpty()) {
            val caret = s + before.length
            Result(newText, caret, caret)
        } else {
            Result(newText, s + before.length, e + before.length)
        }
    }

    /** 给选区覆盖的每一行行首加前缀(标题/列表/任务/引用)。选区为空时作用于光标所在行。 */
    fun linePrefix(text: String, selStart: Int, selEnd: Int, prefix: String): Result {
        val s = selStart.coerceIn(0, text.length)
        val e = selEnd.coerceIn(s, text.length)
        val lineStart = text.lastIndexOf('\n', (s - 1).coerceAtLeast(-1)) + 1
        var lineEnd = text.indexOf('\n', e)
        if (lineEnd < 0) lineEnd = text.length
        val block = text.substring(lineStart, lineEnd)
            .split('\n')
            .joinToString("\n") { prefix + it }
        val newText = text.substring(0, lineStart) + block + text.substring(lineEnd)
        return Result(newText, lineStart, lineStart + block.length)
    }

    /** 在光标处插入整块文本(代码块/分割线),替换掉当前选区(若有)。光标落到插入文本末尾。 */
    fun insertBlock(text: String, selStart: Int, selEnd: Int, block: String): Result {
        val s = selStart.coerceIn(0, text.length)
        val e = selEnd.coerceIn(s, text.length)
        val newText = text.substring(0, s) + block + text.substring(e)
        val caret = s + block.length
        return Result(newText, caret, caret)
    }
}
