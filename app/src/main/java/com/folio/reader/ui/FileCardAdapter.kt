package com.folio.reader.ui

import android.text.format.DateUtils
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.folio.reader.R
import com.folio.reader.data.FileRecord
import com.folio.reader.file.FileType

/** 文件卡片列表:类型角标 + 名称 + 「类型·时间·大小」 + 收藏星;点开 / 切收藏 / 长按管理。 */
class FileCardAdapter(
    private val onOpen: (FileRecord) -> Unit,
    private val onToggleFav: (FileRecord) -> Unit,
    private val onLongPress: (FileRecord) -> Unit
) : RecyclerView.Adapter<FileCardAdapter.Holder>() {

    private val items = mutableListOf<FileRecord>()

    fun submit(list: List<FileRecord>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_file_card, parent, false)
        return Holder(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val badge: TextView = itemView.findViewById(R.id.badge)
        private val name: TextView = itemView.findViewById(R.id.name)
        private val meta: TextView = itemView.findViewById(R.id.meta)
        private val tags: TextView = itemView.findViewById(R.id.tags)
        private val star: ImageButton = itemView.findViewById(R.id.star)

        fun bind(rec: FileRecord) {
            name.text = rec.name
            applyBadge(rec.type)

            val ctx = itemView.context
            val time = DateUtils.getRelativeTimeSpanString(
                rec.lastOpened, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
            )
            val sizeStr = Formatter.formatShortFileSize(ctx, rec.size)
            meta.text = "${typeLabel(rec.type)} · $time · $sizeStr"

            val showTags = com.folio.reader.data.AppPrefs.showTags(ctx) && rec.tags.isNotEmpty()
            tags.visibility = if (showTags) View.VISIBLE else View.GONE
            if (showTags) tags.text = rec.tags.joinToString("  ") { "#$it" }

            star.setImageResource(if (rec.isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star)
            star.setOnClickListener { onToggleFav(rec) }
            itemView.setOnClickListener { onOpen(rec) }
            itemView.setOnLongClickListener { onLongPress(rec); true }
        }

        private fun applyBadge(type: FileType) {
            when (type) {
                FileType.MARKDOWN -> set("MD", R.drawable.bg_badge_md, R.color.badge_md_text)
                FileType.HTML -> set("HTML", R.drawable.bg_badge_html, R.color.badge_html_text)
                FileType.ZIP -> set("ZIP", R.drawable.bg_badge_zip, R.color.badge_zip_text)
                FileType.PDF -> set("PDF", R.drawable.bg_badge_pdf, R.color.badge_pdf_text)
                FileType.UNSUPPORTED -> set("?", R.drawable.bg_badge_md, R.color.badge_md_text)
            }
        }

        private fun set(text: String, bg: Int, textColor: Int) {
            badge.text = text
            badge.setBackgroundResource(bg)
            badge.setTextColor(itemView.context.getColor(textColor))
        }

        private fun typeLabel(type: FileType) = when (type) {
            FileType.MARKDOWN -> "Markdown"
            FileType.HTML -> "HTML"
            FileType.ZIP -> "网页包"
            FileType.PDF -> "PDF"
            FileType.UNSUPPORTED -> "未知"
        }
    }
}
