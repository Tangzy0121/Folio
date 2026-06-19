package com.folio.reader.ui

import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Spannable
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.folio.reader.R
import com.folio.reader.data.LibraryStore
import com.folio.reader.databinding.ActivityMdReaderBinding
import com.folio.reader.reader.CodeBlockEntry
import com.folio.reader.reader.CodeStyle
import com.folio.reader.reader.CodeStyles
import com.folio.reader.reader.MarkdownRenderer
import com.folio.reader.reader.ReaderPrefs
import com.folio.reader.reader.ReaderThemes
import com.folio.reader.reader.ScrollableBlockEntry
import io.noties.markwon.Markwon
import io.noties.markwon.image.AsyncDrawableSpan
import io.noties.markwon.ext.latex.JLatexMathBlock
import io.noties.markwon.recycler.MarkwonAdapter
import io.noties.markwon.recycler.table.TableEntry
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Heading
import org.commonmark.node.Node
import org.commonmark.node.Text
import java.io.File

/** Markdown 阅读页:RecyclerView 渲染 + 电子书主题(背景/字体/字号)+ 目录 + 阅读进度记忆。 */
class MdReaderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMdReaderBinding
    private lateinit var markwon: Markwon
    private var md: String = ""
    private var recordId: String? = null
    private var filePath: String? = null
    private var editing = false

    private var curTextColor = 0
    private var curTypeface: Typeface = Typeface.SANS_SERIF
    private var curSize = ReaderPrefs.DEFAULT_SIZE

    // 记录当前 adapter 是按哪套主题/代码配色构建的,变了才重建(结构色随之刷新)
    private var builtThemeKey: String? = null
    private var builtCodeKey: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMdReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        val path = intent.getStringExtra(EXTRA_PATH)
        filePath = path
        recordId = intent.getStringExtra(EXTRA_ID)
        binding.titleText.text = intent.getStringExtra(EXTRA_NAME) ?: "Markdown"
        if (path.isNullOrEmpty()) { fail("文件路径丢失"); return }
        md = try { File(path).readText() } catch (e: Exception) { fail("读取失败:${e.message}"); return }

        loadStyle()
        binding.recyclerContent.layoutManager = LinearLayoutManager(this)
        binding.recyclerContent.addOnChildAttachStateChangeListener(object :
            RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) = styleChild(view)
            override fun onChildViewDetachedFromWindow(view: View) {}
        })
        buildAndSetAdapter()

        applyReaderStyle()
        binding.btnAa.setOnClickListener { ReaderSettingsSheet(this) { applyReaderStyle() }.show() }
        binding.btnToc.setOnClickListener { showToc() }
        binding.btnEdit.setOnClickListener { toggleEdit() }
        restoreProgress()
    }

    override fun onPause() {
        super.onPause()
        if (editing) saveEdits()
        saveProgress()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // 编辑态:退出编辑(自动保存)回到预览,不直接退出页面
        if (editing) { exitEdit(save = true) } else @Suppress("DEPRECATION") super.onBackPressed()
    }

    // ---- 轻量编辑(仅 MD,写回库内副本)----
    private fun toggleEdit() {
        if (editing) exitEdit(save = true) else enterEdit()
    }

    private fun enterEdit() {
        editing = true
        binding.editArea.setText(md)
        binding.editArea.setTextColor(curTextColor)
        binding.editArea.setBackgroundColor(0x00000000)
        binding.recyclerContent.visibility = View.GONE
        binding.editArea.visibility = View.VISIBLE
        binding.btnToc.visibility = View.GONE
        binding.btnAa.visibility = View.GONE
        binding.btnEdit.setColorFilter(0xFF4FA08B.toInt())  // 编辑中:高亮
        binding.titleText.text = (intent.getStringExtra(EXTRA_NAME) ?: "Markdown") + "(编辑中)"
    }

    private fun exitEdit(save: Boolean) {
        if (save) saveEdits()
        editing = false
        binding.editArea.visibility = View.GONE
        binding.recyclerContent.visibility = View.VISIBLE
        binding.btnToc.visibility = View.VISIBLE
        binding.btnAa.visibility = View.VISIBLE
        binding.btnEdit.setColorFilter(curTextColor)
        binding.titleText.text = intent.getStringExtra(EXTRA_NAME) ?: "Markdown"
    }

    /** 把编辑内容写回库内副本,并重渲染预览。 */
    private fun saveEdits() {
        val path = filePath ?: return
        val newText = binding.editArea.text?.toString() ?: return
        if (newText == md) return
        try {
            val f = File(path)
            f.writeText(newText)
            md = newText
            recordId?.let {
                LibraryStore.updateSize(this, it, f.length())
                LibraryStore.touch(this, it, System.currentTimeMillis())
            }
            buildAndSetAdapter()
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "保存失败:${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ---- 渲染器 / adapter(按主题+代码配色构建)----
    private fun buildAndSetAdapter() {
        val theme = ReaderThemes.byKey(ReaderPrefs.themeKey(this))
        markwon = MarkdownRenderer.create(this, theme)
        val adapter = MarkwonAdapter.builder(R.layout.adapter_default_entry, R.id.md_text)
            .include(FencedCodeBlock::class.java, CodeBlockEntry { currentCodeStyle() })
            .include(JLatexMathBlock::class.java, ScrollableBlockEntry<JLatexMathBlock>(R.layout.adapter_latex_block))
            .include(TableBlock::class.java, TableEntry.create { b ->
                b.tableLayout(R.layout.adapter_table_block, R.id.table_layout).textLayoutIsRoot(R.layout.view_table_cell)
            })
            .build()
        binding.recyclerContent.adapter = adapter
        adapter.setMarkdown(markwon, md)
        adapter.notifyDataSetChanged()
        builtThemeKey = theme.key
        builtCodeKey = ReaderPrefs.codeStyleKey(this)
    }

    private fun currentCodeStyle(): CodeStyle =
        CodeStyles.resolve(ReaderPrefs.codeStyleKey(this), ReaderThemes.byKey(ReaderPrefs.themeKey(this)).isDark)

    /** 主题/代码配色变了:重建 adapter 让结构色(引用条/分割线/代码)刷新,并按比例恢复滚动位。 */
    private fun rebuildPreservingScroll() {
        val rv = binding.recyclerContent
        val max = rv.computeVerticalScrollRange() - rv.height
        val frac = if (max > 0) (rv.computeVerticalScrollOffset().toFloat() / max).coerceIn(0f, 1f) else 0f
        buildAndSetAdapter()
        rv.postDelayed({
            val m = rv.computeVerticalScrollRange() - rv.height
            if (m > 0) rv.scrollBy(0, (frac * m).toInt())
        }, 80)
    }

    // ---- 主题/字体/字号 ----
    private fun loadStyle() {
        val t = ReaderThemes.byKey(ReaderPrefs.themeKey(this))
        curTextColor = t.textColor
        curTypeface = ReaderPrefs.typeface(ReaderPrefs.fontKey(this))
        curSize = ReaderPrefs.sizeSp(this)
    }

    private fun applyReaderStyle() {
        loadStyle()
        // 主题或代码配色变了 → 重建 adapter(结构色随之刷新)
        if (ReaderPrefs.themeKey(this) != builtThemeKey ||
            ReaderPrefs.codeStyleKey(this) != builtCodeKey
        ) rebuildPreservingScroll()
        val t = ReaderThemes.byKey(ReaderPrefs.themeKey(this))
        binding.root.background =
            if (t.gradient != null) GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, t.gradient)
            else ColorDrawable(t.solid!!)
        binding.titleText.setTextColor(curTextColor)
        binding.btnBack.setColorFilter(curTextColor)
        binding.btnAa.setColorFilter(curTextColor)
        binding.btnToc.setColorFilter(curTextColor)
        window.statusBarColor = t.bgColor
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !t.isDark
        restyleAll()
        binding.recyclerContent.post { restyleAll() }
    }

    private fun restyleAll() {
        val rv = binding.recyclerContent
        for (i in 0 until rv.childCount) styleChild(rv.getChildAt(i))
    }

    private fun styleChild(v: View) {
        if (v is ViewGroup) for (i in 0 until v.childCount) styleChild(v.getChildAt(i))
        if (v is TextView) {
            if (v.id == R.id.code_text) return  // 代码块自管配色/等宽,别被正文样式覆盖
            v.setTextColor(curTextColor)
            if (v.id == R.id.md_text) {
                v.setTextSize(TypedValue.COMPLEX_UNIT_SP, curSize.toFloat())
                v.typeface = curTypeface
                wireImageZoom(v)
            }
        }
    }

    // ---- 图片点击放大(给正文里的图片 span 套可点击区段)----
    private fun wireImageZoom(tv: TextView) {
        val text = tv.text as? Spannable ?: return
        val imgs = text.getSpans(0, text.length, AsyncDrawableSpan::class.java)
        if (imgs.isEmpty()) return
        for (img in imgs) {
            val s = text.getSpanStart(img); val e = text.getSpanEnd(img)
            if (s < 0 || e < 0) continue
            if (text.getSpans(s, e, ImageClickSpan::class.java).isNotEmpty()) continue  // 别重复套
            text.setSpan(ImageClickSpan { showImageZoom(img) }, s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        tv.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun showImageZoom(span: AsyncDrawableSpan) {
        val async = span.drawable
        if (!async.hasResult()) { Toast.makeText(this, "图片还没加载好", Toast.LENGTH_SHORT).show(); return }
        ImageZoomDialog.show(this, async.result)
    }

    private class ImageClickSpan(val action: () -> Unit) : ClickableSpan() {
        override fun onClick(widget: View) { action() }
        override fun updateDrawState(ds: android.text.TextPaint) { /* 不加下划线/不改色 */ }
    }

    // ---- 目录 TOC ----
    private fun showToc() {
        val toc = buildToc()
        if (toc.isEmpty()) { Toast.makeText(this, "本文没有标题", Toast.LENGTH_SHORT).show(); return }
        val labels = toc.map { "  ".repeat((it.level - 1).coerceIn(0, 3)) + it.title }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("目录")
            .setItems(labels) { _, w ->
                (binding.recyclerContent.layoutManager as LinearLayoutManager)
                    .scrollToPositionWithOffset(toc[w].index, 0)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private data class TocEntry(val index: Int, val level: Int, val title: String)

    private fun buildToc(): List<TocEntry> {
        val list = ArrayList<TocEntry>()
        var node: Node? = markwon.parse(md).firstChild
        var index = 0
        while (node != null) {
            if (node is Heading) list.add(TocEntry(index, node.level, headingText(node)))
            index++
            node = node.next
        }
        return list
    }

    private fun headingText(h: Node): String {
        val sb = StringBuilder()
        fun rec(n: Node?) {
            var x = n
            while (x != null) {
                if (x is Text) sb.append(x.literal)
                rec(x.firstChild)
                x = x.next
            }
        }
        rec(h.firstChild)
        return sb.toString().ifBlank { "(无标题)" }
    }

    // ---- 阅读进度 ----
    private fun saveProgress() {
        val id = recordId ?: return
        val rv = binding.recyclerContent
        val max = rv.computeVerticalScrollRange() - rv.height
        val frac = if (max > 0) (rv.computeVerticalScrollOffset().toFloat() / max).coerceIn(0f, 1f) else 0f
        LibraryStore.updateProgress(this, id, frac)
    }

    private fun restoreProgress() {
        val id = recordId ?: return
        val frac = LibraryStore.find(this, id)?.progress ?: 0f
        if (frac <= 0f) return
        binding.recyclerContent.postDelayed({
            val rv = binding.recyclerContent
            val max = rv.computeVerticalScrollRange() - rv.height
            if (max > 0) rv.scrollBy(0, (frac * max).toInt())
        }, 250)
    }

    private fun fail(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        finish()
    }

    companion object {
        const val EXTRA_PATH = "extra_path"
        const val EXTRA_NAME = "extra_name"
        const val EXTRA_ID = "extra_id"
    }
}
