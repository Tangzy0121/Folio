package com.folio.reader.ui

import android.app.Dialog
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Spannable
import android.text.Spanned
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
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
import com.folio.reader.reader.MarkdownEditActions
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
import java.util.concurrent.Executors

/**
 * Markdown 阅读页:RecyclerView 渲染 + 电子书主题 + 目录 + 进度;
 * 轻量编辑:分层工具栏(横滑常用 + ⋯全部功能 + Aa标签开关)、撤销/重做、插图/表格、导出、字数、宽屏分栏预览。
 */
class MdReaderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMdReaderBinding
    private lateinit var markwon: Markwon
    private lateinit var mdAdapter: MarkwonAdapter
    private var md: String = ""
    private var recordId: String? = null
    private var filePath: String? = null
    private var editing = false
    private var syncingScroll = false
    private var scrollDriver: View? = null
    private var lastEditScrollY = 0       // EditText 当 driver 时的上一帧 scrollY(算增量)
    private var editToRvRemainder = 0f    // 增量换算到 RV 像素的小数余量,避免取整丢精度
    private var syncScale = -1f           // 平滑后的「RV/Edit 可滚高之比」,EMA 滤波抗估算跳变(-1=未初始化)

    private val isWide by lazy { resources.getBoolean(R.bool.folio_wide_screen) }
    private val previewHandler = Handler(Looper.getMainLooper())
    private val previewRunnable = Runnable { if (editing && isWide) renderPreviewLive() }
    private val previewExecutor = Executors.newSingleThreadExecutor()
    private var previewSeq = 0L
    private var rvPadStart = 0
    private var rvPadEnd = 0

    // 撤销/重做(轻量快照栈)
    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()
    private var undoGuard = false
    private var burstActive = false
    private var burstStart = ""
    private val undoHandler = Handler(Looper.getMainLooper())
    private val commitRunnable = Runnable { flushBurst() }

    // 工具栏标签开关 + 内联按钮引用(切标签时更新)
    private var showToolLabels = false
    private val inlineButtons = ArrayList<Triple<TextView, String, String>>()
    private var moreDialog: Dialog? = null

    private var curTextColor = 0
    private var curTypeface: Typeface = Typeface.SANS_SERIF
    private var curSize = ReaderPrefs.DEFAULT_SIZE

    private var builtThemeKey: String? = null
    private var builtCodeKey: String? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { onImagePicked(it) }
    }

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

        rvPadStart = binding.recyclerContent.paddingStart
        rvPadEnd = binding.recyclerContent.paddingEnd

        loadStyle()
        binding.recyclerContent.layoutManager = LinearLayoutManager(this)
        binding.recyclerContent.setItemViewCacheSize(12)  // 减少滚动同步驱动 RV 时的重绑churn
        binding.recyclerContent.addOnChildAttachStateChangeListener(object :
            RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) = styleChild(view)
            override fun onChildViewDetachedFromWindow(view: View) {}
        })
        buildAndSetAdapter()

        applyReaderStyle()
        setupToolbar()
        binding.editArea.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                if (!undoGuard && !burstActive) { burstStart = s?.toString() ?: ""; burstActive = true }
            }
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (undoGuard) return
                updateWordCount()
                if (editing && isWide) {
                    previewHandler.removeCallbacks(previewRunnable)
                    previewHandler.postDelayed(previewRunnable, 400)
                }
                undoHandler.removeCallbacks(commitRunnable)
                undoHandler.postDelayed(commitRunnable, 600)
            }
        })
        binding.btnAa.setOnClickListener { ReaderSettingsSheet(this) { applyReaderStyle() }.show() }
        binding.btnToc.setOnClickListener { showToc() }
        binding.btnEdit.setOnClickListener { toggleEdit() }
        binding.btnToolLabels.setOnClickListener { toggleLabels() }
        binding.btnToolMore.setOnClickListener { showMoreSheet() }
        binding.btnToolSwap.setOnClickListener {
            ReaderPrefs.setSplitSourceLeft(this, !ReaderPrefs.splitSourceLeft(this))
            if (editing && isWide) { applySplitOrder(); renderPreviewLive() }
        }
        setupScrollSync()
        restoreProgress()

        if (intent.getBooleanExtra(EXTRA_START_IN_EDIT, false)) {
            binding.recyclerContent.post { if (!editing) enterEdit() }
        }
    }

    override fun onPause() {
        super.onPause()
        if (editing) saveEdits()
        saveProgress()
    }

    override fun onDestroy() {
        super.onDestroy()
        previewExecutor.shutdownNow()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (editing) { exitEdit(save = true) } else @Suppress("DEPRECATION") super.onBackPressed()
    }

    // ---- 轻量编辑 ----
    private fun toggleEdit() { if (editing) exitEdit(save = true) else enterEdit() }

    private fun enterEdit() {
        editing = true
        undoStack.clear(); redoStack.clear(); burstActive = false
        undoGuard = true
        binding.editArea.setText(md)
        undoGuard = false
        binding.editArea.setTextColor(curTextColor)
        binding.editArea.setBackgroundColor(0x00000000)
        binding.editToolbar.visibility = View.VISIBLE
        binding.wordCount.visibility = View.VISIBLE
        updateWordCount()
        if (isWide) {
            binding.editorSplit.orientation = LinearLayout.HORIZONTAL
            binding.recyclerContent.setPaddingRelative(
                dp(12), binding.recyclerContent.paddingTop, dp(12), binding.recyclerContent.paddingBottom)
            binding.recyclerContent.visibility = View.VISIBLE
            binding.editArea.visibility = View.VISIBLE
            binding.btnToolSwap.visibility = View.VISIBLE
            applySplitOrder()
            renderPreviewLive()
        } else {
            binding.recyclerContent.visibility = View.GONE
            binding.editArea.visibility = View.VISIBLE
        }
        binding.btnToc.visibility = View.GONE
        binding.btnAa.visibility = View.GONE
        binding.btnEdit.setColorFilter(ContextCompat.getColor(this, R.color.folio_accent))
        binding.titleText.text = (intent.getStringExtra(EXTRA_NAME) ?: "Markdown") + "(编辑中)"
    }

    private fun exitEdit(save: Boolean) {
        if (save) saveEdits()
        editing = false
        previewSeq++   // 作废在途的后台预览结果
        previewHandler.removeCallbacks(previewRunnable)
        undoHandler.removeCallbacks(commitRunnable)
        binding.editToolbar.visibility = View.GONE
        binding.wordCount.visibility = View.GONE
        binding.btnToolSwap.visibility = View.GONE
        binding.editorSplit.orientation = LinearLayout.VERTICAL
        setSplitChild(binding.recyclerContent, horizontal = false)
        setSplitChild(binding.editArea, horizontal = false)
        binding.recyclerContent.setPaddingRelative(
            rvPadStart, binding.recyclerContent.paddingTop, rvPadEnd, binding.recyclerContent.paddingBottom)
        binding.editArea.visibility = View.GONE
        binding.recyclerContent.visibility = View.VISIBLE
        binding.btnToc.visibility = View.VISIBLE
        binding.btnAa.visibility = View.VISIBLE
        binding.btnEdit.setColorFilter(curTextColor)
        binding.titleText.text = intent.getStringExtra(EXTRA_NAME) ?: "Markdown"
    }

    /** 按偏好排分栏左右(源码/预览),并设好横排 LayoutParams。 */
    private fun applySplitOrder() {
        val split = binding.editorSplit
        val sourceLeft = ReaderPrefs.splitSourceLeft(this)
        split.removeAllViews()
        if (sourceLeft) { split.addView(binding.editArea); split.addView(binding.recyclerContent) }
        else { split.addView(binding.recyclerContent); split.addView(binding.editArea) }
        setSplitChild(binding.editArea, horizontal = true)
        setSplitChild(binding.recyclerContent, horizontal = true)
    }

    private fun setSplitChild(v: View, horizontal: Boolean) {
        val lp = v.layoutParams as LinearLayout.LayoutParams
        if (horizontal) { lp.width = 0; lp.height = LinearLayout.LayoutParams.MATCH_PARENT }
        else { lp.width = LinearLayout.LayoutParams.MATCH_PARENT; lp.height = 0 }
        lp.weight = 1f
        v.layoutParams = lp
    }

    /**
     * 实时预览:把整篇 markdown 的解析挪到后台单线程,主线程只贴预解析好的 Node——
     * 去掉打字时主线程全量解析这个最大卡顿源(Markwon 官方推荐的后台处理流)。
     * previewSeq 在主线程自增,post 回来时只应用最新一拍,丢弃过期结果。
     */
    private fun renderPreviewLive() {
        val text = binding.editArea.text?.toString() ?: ""
        val mk = markwon                 // 捕获当前渲染器(主题切换会重建 markwon)
        val seq = ++previewSeq
        previewExecutor.execute {
            val node = try { mk.parse(text) } catch (e: Exception) { return@execute }
            previewHandler.post {
                if (seq != previewSeq || !editing || !isWide) return@post
                mdAdapter.setParsedMarkdown(mk, node)
                mdAdapter.notifyDataSetChanged()
            }
        }
    }

    /**
     * 宽屏分栏编辑时,源码与预览按滚动比例双向同步,避免左右对不上。
     * 关键:用「手指当前所在的一侧」当 driver,只让 driver 推从动侧,从动侧自身的滚动事件一律忽略——
     * 根除两侧因比例非精确互逆而来回拽的微抖/卡顿。
     */
    private fun setupScrollSync() {
        @Suppress("ClickableViewAccessibility")
        binding.editArea.setOnTouchListener { _, ev ->
            if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
                scrollDriver = binding.editArea
                lastEditScrollY = binding.editArea.scrollY   // 起一段新增量基准
                editToRvRemainder = 0f
                syncScale = -1f                              // 重置平滑比例,下一帧用即时值起步
            }
            false
        }
        binding.recyclerContent.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                if (e.actionMasked == MotionEvent.ACTION_DOWN) scrollDriver = binding.recyclerContent
                return false
            }
        })
        binding.editArea.setOnScrollChangeListener { _, _, _, _, _ ->
            if (!editing || !isWide || syncingScroll || scrollDriver !== binding.editArea) return@setOnScrollChangeListener
            val cur = binding.editArea.scrollY
            val dyEdit = cur - lastEditScrollY
            lastEditScrollY = cur
            if (dyEdit == 0) return@setOnScrollChangeListener
            syncingScroll = true
            scrollPreviewByEditDelta(dyEdit)   // 按增量比例推,不读 RV 绝对位置→不被估算跳变带抖
            syncingScroll = false
        }
        binding.recyclerContent.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (!editing || !isWide || syncingScroll || dy == 0 || scrollDriver !== binding.recyclerContent) return
                syncingScroll = true
                scrollEditorToFraction(rvFraction())
                syncingScroll = false
            }
        })
    }

    private fun rvFraction(): Float {
        val rv = binding.recyclerContent
        val range = (rv.computeVerticalScrollRange() - rv.computeVerticalScrollExtent()).coerceAtLeast(1)
        return (rv.computeVerticalScrollOffset().toFloat() / range).coerceIn(0f, 1f)
    }

    /**
     * EditText 滚了 dyEdit 像素,按「两侧可滚动总高之比」换算成 RV 应滚的像素并 scrollBy。
     * 用相对增量(非绝对目标)→ RV 变高 item 导致的 range 估算跳变不会造成回弹抖动。
     * 小数余量累加,避免每帧取整丢精度。
     */
    private fun scrollPreviewByEditDelta(dyEdit: Int) {
        val et = binding.editArea
        val layout = et.layout ?: return
        val editRange = layout.height + et.totalPaddingTop + et.totalPaddingBottom - et.height
        if (editRange <= 0) return
        val rv = binding.recyclerContent
        val rvRange = rv.computeVerticalScrollRange() - rv.computeVerticalScrollExtent()
        if (rvRange <= 0) return
        // RV 总高是估算值,逐帧会跳;对比例做 EMA 平滑,避免新块绑定时速度忽快忽慢的轻微抖动
        val instant = rvRange.toFloat() / editRange
        syncScale = if (syncScale <= 0f) instant else syncScale + (instant - syncScale) * 0.15f
        val scaled = dyEdit.toFloat() * syncScale + editToRvRemainder
        val px = scaled.toInt()
        editToRvRemainder = scaled - px
        if (px != 0) rv.scrollBy(0, px)
    }

    private fun scrollEditorToFraction(f: Float) {
        val et = binding.editArea
        val layout = et.layout ?: return
        val contentH = layout.height + et.totalPaddingTop + et.totalPaddingBottom
        val range = contentH - et.height
        if (range <= 0) return
        et.scrollTo(0, (range * f).toInt())
    }

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
            toast("已保存")
        } catch (e: Exception) { toast("保存失败:${e.message}") }
    }

    // ---- 工具栏:动作集 / 内联 / 分组网格 ----
    private class Tool(val sym: String, val label: String, val run: () -> Unit)

    private val tools: Map<String, Tool> by lazy {
        fun tx(b: String, a: String): () -> Unit = { applyAction { t, s, e -> MarkdownEditActions.wrap(t, s, e, b, a) } }
        fun ln(p: String): () -> Unit = { applyAction { t, s, e -> MarkdownEditActions.linePrefix(t, s, e, p) } }
        fun blk(x: String): () -> Unit = { applyAction { t, s, e -> MarkdownEditActions.insertBlock(t, s, e, x) } }
        mapOf(
            "bold" to Tool("B", "粗体", tx("**", "**")),
            "italic" to Tool("I", "斜体", tx("*", "*")),
            "strike" to Tool("S", "删除线", tx("~~", "~~")),
            "code" to Tool("</>", "行内码", tx("`", "`")),
            "h" to Tool("H", "标题", ln("# ")),
            "ul" to Tool("•", "列表", ln("- ")),
            "ol" to Tool("1.", "编号", ln("1. ")),
            "task" to Tool("✓", "待办", ln("- [ ] ")),
            "quote" to Tool("❝", "引用", ln("> ")),
            "hr" to Tool("—", "分割线", blk("\n---\n")),
            "link" to Tool("🔗", "链接", tx("[", "](url)")),
            "image" to Tool("🖼", "图片", { pickImage.launch("image/*") }),
            "fence" to Tool("▣", "代码块", blk("\n```\n\n```\n")),
            "table" to Tool("▦", "表格", blk("\n| 列1 | 列2 |\n| --- | --- |\n|  |  |\n")),
            "undo" to Tool("↶", "撤销", { undo() }),
            "redo" to Tool("↷", "重做", { redo() }),
            "export" to Tool("⤓", "导出", { exportCurrent() })
        )
    }
    private val inlineOrder = listOf(
        "bold", "h", "ul", "task", "link", "fence",
        "italic", "strike", "ol", "quote", "code", "hr", "image", "table", "undo", "redo", "export")
    private val gridGroups = listOf(
        "文本" to listOf("bold", "italic", "strike", "code"),
        "段落" to listOf("h", "ul", "ol", "task", "quote", "hr"),
        "插入" to listOf("link", "image", "fence", "table"),
        "操作" to listOf("undo", "redo", "export"))

    private fun setupToolbar() {
        inlineButtons.clear()
        binding.toolbarButtons.removeAllViews()
        for (id in inlineOrder) {
            val tool = tools[id] ?: continue
            val b = TextView(this).apply {
                text = tool.sym
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(ContextCompat.getColor(this@MdReaderActivity, R.color.jiyue_text_primary))
                gravity = Gravity.CENTER
                minWidth = dp(34)
                setPadding(dp(10), dp(6), dp(10), dp(6))
                background = toolBg()
                isClickable = true
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(5) }
                layoutParams = lp
                setOnClickListener { tool.run() }
            }
            binding.toolbarButtons.addView(b)
            inlineButtons.add(Triple(b, tool.sym, tool.label))
        }
        refreshToolLabels()
    }

    private fun toolBg() = GradientDrawable().apply {
        cornerRadius = dp(8).toFloat()
        setColor(ContextCompat.getColor(this@MdReaderActivity, R.color.folio_surface2))
        setStroke(dp(1), ContextCompat.getColor(this@MdReaderActivity, R.color.folio_line))
    }

    private fun toggleLabels() {
        showToolLabels = !showToolLabels
        refreshToolLabels()
        binding.btnToolLabels.setTextColor(
            ContextCompat.getColor(this,
                if (showToolLabels) R.color.folio_accent else R.color.jiyue_text_secondary))
    }

    private fun refreshToolLabels() {
        for ((b, sym, label) in inlineButtons) b.text = if (showToolLabels) "$sym $label" else sym
    }

    private fun showMoreSheet() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(18))
        }
        box.addView(TextView(this).apply {
            text = "全部功能"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ContextCompat.getColor(this@MdReaderActivity, R.color.jiyue_text_primary))
            setPadding(dp(4), dp(2), 0, dp(8))
        })
        for ((group, ids) in gridGroups) {
            box.addView(TextView(this).apply {
                text = group
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(ContextCompat.getColor(this@MdReaderActivity, R.color.jiyue_text_secondary))
                setPadding(dp(4), dp(10), 0, dp(6))
            })
            var rowLayout: LinearLayout? = null
            ids.forEachIndexed { i, id ->
                val tool = tools[id] ?: return@forEachIndexed
                if (i % 4 == 0) {
                    rowLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                    box.addView(rowLayout)
                }
                rowLayout?.addView(gridCell(tool))
            }
        }
        val d = AdaptiveSheet.create(this, box)
        moreDialog = d
        d.setOnDismissListener { moreDialog = null }
        d.show()
    }

    private fun gridCell(tool: Tool): View {
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(12), dp(6), dp(12))
            background = toolBg()
            isClickable = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
            setOnClickListener { tool.run(); moreDialog?.dismiss() }
        }
        cell.addView(TextView(this).apply {
            text = tool.sym
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ContextCompat.getColor(this@MdReaderActivity, R.color.folio_accent_strong))
            gravity = Gravity.CENTER
        })
        cell.addView(TextView(this).apply {
            text = tool.label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(ContextCompat.getColor(this@MdReaderActivity, R.color.jiyue_text_primary))
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, 0)
        })
        return cell
    }

    private fun applyAction(fn: (String, Int, Int) -> MarkdownEditActions.Result) {
        flushBurst()
        val ta = binding.editArea
        val text = ta.text?.toString() ?: ""
        pushUndoState(text)
        val a = ta.selectionStart.coerceIn(0, text.length)
        val b = ta.selectionEnd.coerceIn(0, text.length)
        val s = minOf(a, b); val e = maxOf(a, b)
        val r = fn(text, s, e)
        undoGuard = true
        ta.setText(r.text)
        ta.setSelection(r.selStart.coerceIn(0, r.text.length), r.selEnd.coerceIn(0, r.text.length))
        undoGuard = false
        ta.requestFocus()
        updateWordCount()
        if (editing && isWide) renderPreviewLive()
    }

    // ---- 撤销/重做 ----
    private fun pushUndoState(s: String) {
        undoStack.addLast(s); if (undoStack.size > 60) undoStack.removeFirst(); redoStack.clear()
    }
    private fun flushBurst() {
        if (burstActive) {
            undoStack.addLast(burstStart); if (undoStack.size > 60) undoStack.removeFirst(); redoStack.clear()
            burstActive = false
        }
    }
    private fun undo() {
        val cur = binding.editArea.text?.toString() ?: ""
        if (burstActive) { burstActive = false; redoStack.addLast(cur); setEditGuarded(burstStart); return }
        if (undoStack.isEmpty()) { toast("没有可撤销的"); return }
        redoStack.addLast(cur); setEditGuarded(undoStack.removeLast())
    }
    private fun redo() {
        if (redoStack.isEmpty()) { toast("没有可重做的"); return }
        val cur = binding.editArea.text?.toString() ?: ""
        undoStack.addLast(cur); setEditGuarded(redoStack.removeLast())
    }
    private fun setEditGuarded(s: String) {
        undoGuard = true
        binding.editArea.setText(s)
        binding.editArea.setSelection(s.length)
        undoGuard = false
        updateWordCount()
        if (editing && isWide) renderPreviewLive()
    }

    private fun updateWordCount() {
        val n = (binding.editArea.text?.toString() ?: "").count { !it.isWhitespace() }
        binding.wordCount.text = "$n 字"
    }

    // ---- 插入图片 ----
    private fun onImagePicked(uri: Uri) {
        try {
            val ext = when (contentResolver.getType(uri)) {
                "image/png" -> "png"; "image/webp" -> "webp"; "image/gif" -> "gif"; else -> "jpg"
            }
            val dir = File(filesDir, "library/images").apply { mkdirs() }
            val out = File(dir, "img_${System.currentTimeMillis()}.$ext")
            val ok = contentResolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { input.copyTo(it) }; true
            } ?: false
            if (!ok) { toast("读取图片失败"); return }
            applyAction { t, s, e -> MarkdownEditActions.insertBlock(t, s, e, "![](file://${out.absolutePath})\n") }
        } catch (e: Exception) { toast("插入图片失败:${e.message}") }
    }

    // ---- 导出 / 分享当前文本 ----
    private fun exportCurrent() {
        try {
            val name = intent.getStringExtra(EXTRA_NAME) ?: "document.md"
            val dir = File(cacheDir, "share").apply { mkdirs() }
            val out = File(dir, name)
            out.writeText(binding.editArea.text?.toString() ?: md)
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", out)
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/markdown"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "导出 / 分享"))
        } catch (e: Exception) { toast("导出失败:${e.message}") }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    // ---- 渲染器 / adapter ----
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
        mdAdapter = adapter
        binding.recyclerContent.adapter = adapter
        adapter.setMarkdown(markwon, md)
        adapter.notifyDataSetChanged()
        builtThemeKey = theme.key
        builtCodeKey = ReaderPrefs.codeStyleKey(this)
    }

    private fun currentCodeStyle(): CodeStyle =
        CodeStyles.resolve(ReaderPrefs.codeStyleKey(this), ReaderThemes.byKey(ReaderPrefs.themeKey(this)).isDark)

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
        if (!editing) binding.btnEdit.setColorFilter(curTextColor)
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
            if (v.id == R.id.code_text) {
                // 代码块:字号跟随阅读设置(略小保紧凑);保持 monospace、不碰颜色(颜色由高亮 span 决定)
                v.setTextSize(TypedValue.COMPLEX_UNIT_SP,
                    (curSize - 1).coerceAtLeast(ReaderPrefs.MIN_SIZE).toFloat())
                return
            }
            v.setTextColor(curTextColor)
            if (v.id == R.id.md_text) {
                v.setTextSize(TypedValue.COMPLEX_UNIT_SP, curSize.toFloat())
                v.typeface = curTypeface
                wireImageZoom(v)
            } else {
                // 表格单元格(无 id 的 TextView):字号跟随阅读设置,略小保表格紧凑
                v.setTextSize(TypedValue.COMPLEX_UNIT_SP,
                    (curSize - 1).coerceAtLeast(ReaderPrefs.MIN_SIZE).toFloat())
                v.typeface = curTypeface
            }
        }
    }

    // ---- 图片点击放大 ----
    private fun wireImageZoom(tv: TextView) {
        val text = tv.text as? Spannable ?: return
        val imgs = text.getSpans(0, text.length, AsyncDrawableSpan::class.java)
        if (imgs.isEmpty()) return
        for (img in imgs) {
            val s = text.getSpanStart(img); val e = text.getSpanEnd(img)
            if (s < 0 || e < 0) continue
            if (text.getSpans(s, e, ImageClickSpan::class.java).isNotEmpty()) continue
            text.setSpan(ImageClickSpan { showImageZoom(img) }, s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        tv.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun showImageZoom(span: AsyncDrawableSpan) {
        val async = span.drawable
        if (!async.hasResult()) { toast("图片还没加载好"); return }
        ImageZoomDialog.show(this, async.result)
    }

    private class ImageClickSpan(val action: () -> Unit) : ClickableSpan() {
        override fun onClick(widget: View) { action() }
        override fun updateDrawState(ds: android.text.TextPaint) { }
    }

    // ---- 目录 TOC ----
    private fun showToc() {
        val toc = buildToc()
        if (toc.isEmpty()) { toast("本文没有标题"); return }
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

    private fun fail(msg: String) { toast(msg); finish() }

    companion object {
        const val EXTRA_PATH = "extra_path"
        const val EXTRA_NAME = "extra_name"
        const val EXTRA_ID = "extra_id"
        const val EXTRA_START_IN_EDIT = "extra_start_in_edit"
    }
}
