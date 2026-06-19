package com.folio.reader.reader

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.folio.reader.R
import io.noties.markwon.Markwon
import io.noties.markwon.recycler.MarkwonAdapter
import org.commonmark.node.FencedCodeBlock

/**
 * 围栏代码块渲染:按语言做轻量语法高亮(CodeHighlighter)+ 套代码配色(CodeStyle)圆角底色,
 * 外层保留 HorizontalScrollView 负责长行横滑。配色在每次 bind 时按 styleProvider 取当前值,
 * 故主题/配色切换后只要重建 adapter 即可刷新。
 */
class CodeBlockEntry(
    private val styleProvider: () -> CodeStyle
) : MarkwonAdapter.Entry<FencedCodeBlock, CodeBlockEntry.Holder>() {

    override fun createHolder(inflater: LayoutInflater, parent: ViewGroup): Holder =
        Holder(inflater.inflate(R.layout.adapter_code_block, parent, false))

    override fun bindHolder(markwon: Markwon, holder: Holder, node: FencedCodeBlock) {
        val style = styleProvider()
        val lang = node.info?.trim()?.substringBefore(' ')?.ifBlank { null }
        var code = node.literal ?: ""
        if (code.endsWith("\n")) code = code.dropLast(1)

        holder.textView.setTextColor(style.fg)
        holder.textView.text = CodeHighlighter.highlight(code, lang, style)

        val radius = 10f * holder.itemView.resources.displayMetrics.density
        holder.itemView.background = GradientDrawable().apply {
            setColor(style.bg)
            cornerRadius = radius
        }
    }

    class Holder(itemView: View) : MarkwonAdapter.Holder(itemView) {
        val textView: TextView = requireView(R.id.code_text)
    }
}
