package com.folio.reader.reader

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.folio.reader.R
import io.noties.markwon.Markwon
import io.noties.markwon.recycler.MarkwonAdapter
import org.commonmark.node.Node

/**
 * 通用「宽内容」条目:把一个 block 渲到 TextView,外层布局用 HorizontalScrollView 包住,
 * 于是超宽的代码 / 公式可以左右滑动看全。代码块与 LaTeX 块复用同一逻辑、各用各的布局。
 */
class ScrollableBlockEntry<N : Node>(
    private val layoutRes: Int
) : MarkwonAdapter.Entry<N, ScrollableBlockEntry.Holder>() {

    override fun createHolder(inflater: LayoutInflater, parent: ViewGroup): Holder =
        Holder(inflater.inflate(layoutRes, parent, false))

    override fun bindHolder(markwon: Markwon, holder: Holder, node: N) {
        markwon.setParsedMarkdown(holder.textView, markwon.render(node))
    }

    class Holder(itemView: View) : MarkwonAdapter.Holder(itemView) {
        val textView: TextView = requireView(R.id.md_text)
    }
}
