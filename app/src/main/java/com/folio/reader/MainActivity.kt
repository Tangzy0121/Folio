package com.folio.reader

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.Formatter
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.folio.reader.data.AppPrefs
import com.folio.reader.data.FileRecord
import com.folio.reader.data.LibraryStore
import com.folio.reader.databinding.ActivityMainBinding
import com.folio.reader.file.FileCopier
import com.folio.reader.file.FileIntentHandler
import com.folio.reader.file.FileType
import com.folio.reader.file.FileTypeDetector
import com.folio.reader.file.ZipExtractor
import com.folio.reader.ui.AdaptiveSheet
import com.folio.reader.ui.FileActionSheet
import com.folio.reader.ui.FileCardAdapter
import com.folio.reader.ui.GuideActivity
import com.folio.reader.ui.HtmlReaderActivity
import com.folio.reader.ui.MdReaderActivity
import com.folio.reader.ui.PdfReaderActivity
import com.folio.reader.ui.SettingsSheet
import com.folio.reader.ui.TagManagerSheet
import com.folio.reader.ui.TagPickerSheet
import java.io.File

/** 首页:本地文件单列表 + 筛选(全部/收藏/标签)+ FAB 导入/新建。 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var fileAdapter: FileCardAdapter
    private var filter = "全部"   // 全部 / 收藏 / 某标签

    private val openDoc = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { handlePickedUri(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fileAdapter = FileCardAdapter(
            onOpen = { openRecord(it) },
            onToggleFav = { toggleFav(it) },
            onLongPress = { showFileActions(it) }
        )
        binding.recyclerFiles.layoutManager = LinearLayoutManager(this)
        binding.recyclerFiles.adapter = fileAdapter

        binding.btnSettings.setOnClickListener { SettingsSheet(this) { renderList() }.show() }
        binding.btnManageTags.setOnClickListener { showTagManager() }
        binding.fabAdd.setOnClickListener { showAddMenu() }

        handleIncomingIntent(intent)
        showLastCrashIfAny()
        maybeShowWelcome()
    }

    override fun onResume() {
        super.onResume()
        renderList()
    }

    // ---- 列表渲染 ----
    private fun renderList() {
        val all = LibraryStore.recent(this)
        val tags = all.flatMap { it.tags }.filter { it.isNotBlank() }.distinct().sortedBy { it.lowercase() }
        if (filter != "全部" && filter != "收藏" && !tags.contains(filter)) filter = "全部"
        buildChips(binding.chips, filter, tags) { filter = it; renderList() }
        val list = when (filter) {
            "全部" -> all
            "收藏" -> all.filter { it.isFavorite }
            else -> all.filter { it.tags.contains(filter) }
        }
        fileAdapter.submit(list)
        val empty = list.isEmpty()
        binding.recyclerFiles.visibility = if (empty) View.GONE else View.VISIBLE
        binding.emptyView.visibility = if (empty) View.VISIBLE else View.GONE
        binding.emptyView.text = when (filter) {
            "收藏" -> "还没有收藏的文件"
            "全部" -> "还没有文件\n用右下 ＋ 导入或新建"
            else -> "没有「$filter」的文件"
        }
    }

    /** 单选 chip:全部 / ★收藏 / 各标签。 */
    private fun buildChips(group: ChipGroup, selected: String, tags: List<String>, onPick: (String) -> Unit) {
        group.removeAllViews()
        val opts = listOf("全部", "收藏") + tags
        for (o in opts) {
            group.addView(Chip(this).apply {
                text = if (o == "收藏") "★ 收藏" else o
                isCheckable = true
                isChecked = o == selected
                setOnClickListener { onPick(o) }
            })
        }
    }

    private fun toggleFav(rec: FileRecord) {
        LibraryStore.toggleFavorite(this, rec.id); renderList()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ---- FAB:导入 / 新建 ----
    private fun showAddMenu() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(10), 0, dp(12)) }
        lateinit var dialog: Dialog
        fun row(iconRes: Int, label: String, action: () -> Unit): View {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(20), dp(15), dp(20), dp(15))
                isClickable = true
                setOnClickListener { dialog.dismiss(); action() }
            }
            row.addView(ImageView(this).apply {
                setImageResource(iconRes)
                setColorFilter(ContextCompat.getColor(this@MainActivity, R.color.jiyue_text_primary))
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
            })
            row.addView(TextView(this).apply {
                text = label
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.jiyue_text_primary))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setPadding(dp(16), 0, 0, 0)
            })
            return row
        }
        box.addView(row(R.drawable.ic_doc, "导入文件") { openDoc.launch(arrayOf("*/*")) })
        box.addView(row(R.drawable.ic_edit, getString(R.string.new_doc_action)) { showNewDocDialog() })
        dialog = AdaptiveSheet.create(this, box)
        dialog.show()
    }

    // ---- 新建空白文档 ----
    private fun showNewDocDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_rename, null)
        val input = view.findViewById<TextInputEditText>(R.id.renameInput)
        input.setText(getString(R.string.new_doc_default))
        input.setSelection(input.text?.length ?: 0)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.new_doc_title)
            .setView(view)
            .setPositiveButton("创建") { _, _ -> createNewDoc(input.text.toString()) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun createNewDoc(rawName: String) {
        val base = rawName.trim().ifEmpty { getString(R.string.new_doc_default) }
        val withExt = if (base.endsWith(".md", true) || base.endsWith(".markdown", true)) base else "$base.md"
        val safe = withExt.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        try {
            val dir = File(filesDir, "library/files").apply { mkdirs() }
            val id = "$withExt|${System.currentTimeMillis()}"
            val file = File(dir, "${kotlin.math.abs(id.hashCode())}_$safe")
            file.writeText("")
            val rec = FileRecord(
                id = id, name = withExt, type = FileType.MARKDOWN, size = 0L,
                storedPath = file.absolutePath, entryPath = null,
                lastOpened = System.currentTimeMillis(), isFavorite = false, progress = 0f
            )
            LibraryStore.upsert(this, rec)
            startActivity(Intent(this, MdReaderActivity::class.java).apply {
                putExtra(MdReaderActivity.EXTRA_PATH, rec.storedPath)
                putExtra(MdReaderActivity.EXTRA_NAME, rec.name)
                putExtra(MdReaderActivity.EXTRA_ID, rec.id)
                putExtra(MdReaderActivity.EXTRA_START_IN_EDIT, true)
            })
        } catch (e: Exception) { toast("新建失败:${e.message}") }
    }

    // ---- 首次欢迎 ----
    private fun maybeShowWelcome() {
        if (AppPrefs.seenGuide(this)) return
        AppPrefs.setSeenGuide(this, true)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.welcome_title)
            .setMessage(R.string.welcome_msg)
            .setPositiveButton(R.string.welcome_view) { _, _ -> startActivity(Intent(this, GuideActivity::class.java)) }
            .setNegativeButton(R.string.welcome_skip, null)
            .show()
    }

    private fun showTagManager() {
        TagManagerSheet(
            ctx = this,
            allTags = { LibraryStore.allTags(this) },
            selected = { emptySet() },
            onToggleFilter = { },
            onRename = { old, new -> LibraryStore.renameTag(this, old, new) },
            onDelete = { t -> LibraryStore.deleteTag(this, t) },
            onChanged = { renderList() }
        ).show()
    }

    // ---- 打开入口 ----
    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null || !FileIntentHandler.isFileIntent(intent)) return
        val uri = FileIntentHandler.extractUri(intent)
        if (uri == null) { toast("没拿到文件,换个方式试试"); return }
        handlePickedUri(uri)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handlePickedUri(uri: Uri) {
        val name = FileCopier.queryName(this, uri) ?: "document"
        val head = FileCopier.peekHead(this, uri)
        val type = FileTypeDetector.detect(name, head)
        when (type) {
            FileType.MARKDOWN, FileType.HTML, FileType.PDF -> importAndOpen(uri, name, type)
            FileType.ZIP -> importAndOpenZip(uri, name)
            FileType.UNSUPPORTED -> toast("不支持的文件类型(目前 MD / HTML / ZIP / PDF / txt)")
        }
    }

    private fun importAndOpen(uri: Uri, name: String, type: FileType) {
        try {
            val lib = FileCopier.copyToLibrary(this, uri, name)
            val existing = LibraryStore.find(this, lib.id)
            val rec = FileRecord(
                id = lib.id, name = existing?.name ?: lib.name, type = type, size = lib.size,
                storedPath = lib.path, entryPath = null,
                lastOpened = System.currentTimeMillis(),
                isFavorite = existing?.isFavorite ?: false, progress = existing?.progress ?: 0f
            )
            LibraryStore.upsert(this, rec)
            openRecord(rec)
        } catch (e: Exception) { toast("打开失败:${e.message}") }
    }

    private fun importAndOpenZip(uri: Uri, name: String) {
        try {
            val lib = FileCopier.copyToLibrary(this, uri, name)
            val extractDir = File(filesDir, "library/zip/${kotlin.math.abs(lib.id.hashCode())}")
            val result = ZipExtractor.extract(File(lib.path), extractDir)
            File(lib.path).delete()
            val existing = LibraryStore.find(this, lib.id)
            val rec = FileRecord(
                id = lib.id, name = existing?.name ?: lib.name, type = FileType.ZIP, size = lib.size,
                storedPath = extractDir.absolutePath, entryPath = result.entry,
                lastOpened = System.currentTimeMillis(),
                isFavorite = existing?.isFavorite ?: false, progress = existing?.progress ?: 0f
            )
            LibraryStore.upsert(this, rec)
            openRecord(rec)
        } catch (e: Exception) { toast("打开 ZIP 失败:${e.message}") }
    }

    private fun openRecord(rec: FileRecord) {
        if (!File(rec.storedPath).exists()) {
            toast("文件已不在,已从列表移除")
            LibraryStore.delete(this, rec.id)
            renderList()
            return
        }
        LibraryStore.touch(this, rec.id, System.currentTimeMillis())
        when (rec.type) {
            FileType.MARKDOWN -> startActivity(Intent(this, MdReaderActivity::class.java).apply {
                putExtra(MdReaderActivity.EXTRA_PATH, rec.storedPath)
                putExtra(MdReaderActivity.EXTRA_NAME, rec.name)
                putExtra(MdReaderActivity.EXTRA_ID, rec.id)
            })
            FileType.HTML -> startActivity(Intent(this, HtmlReaderActivity::class.java).apply {
                putExtra(HtmlReaderActivity.EXTRA_PATH, rec.storedPath)
                putExtra(HtmlReaderActivity.EXTRA_NAME, rec.name)
                putExtra(HtmlReaderActivity.EXTRA_ID, rec.id)
            })
            FileType.ZIP -> {
                val entry = rec.entryPath
                if (entry == null) { toast("网页包入口丢失"); return }
                startActivity(Intent(this, HtmlReaderActivity::class.java).apply {
                    putExtra(HtmlReaderActivity.EXTRA_ZIP_DIR, rec.storedPath)
                    putExtra(HtmlReaderActivity.EXTRA_ENTRY, entry)
                    putExtra(HtmlReaderActivity.EXTRA_NAME, rec.name)
                    putExtra(HtmlReaderActivity.EXTRA_ID, rec.id)
                })
            }
            FileType.PDF -> startActivity(Intent(this, PdfReaderActivity::class.java).apply {
                putExtra(PdfReaderActivity.EXTRA_PATH, rec.storedPath)
                putExtra(PdfReaderActivity.EXTRA_NAME, rec.name)
                putExtra(PdfReaderActivity.EXTRA_ID, rec.id)
            })
            FileType.UNSUPPORTED -> toast("不支持的类型")
        }
    }

    // ---- 文件管理(长按)----
    private fun showFileActions(rec: FileRecord) {
        val canShare = rec.type == FileType.MARKDOWN || rec.type == FileType.HTML
        val typeLabel = when (rec.type) {
            FileType.MARKDOWN -> "Markdown"; FileType.HTML -> "HTML"
            FileType.ZIP -> "网页包"; FileType.PDF -> "PDF"
            FileType.UNSUPPORTED -> "文件"
        }
        val sub = "$typeLabel · ${Formatter.formatShortFileSize(this, rec.size)}"
        val actions = buildList {
            add(FileActionSheet.Action(
                if (rec.isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star,
                if (rec.isFavorite) "取消收藏" else "收藏"
            ) { LibraryStore.toggleFavorite(this@MainActivity, rec.id); renderList() })
            add(FileActionSheet.Action(R.drawable.ic_edit, "重命名") { showRenameDialog(rec) })
            add(FileActionSheet.Action(R.drawable.ic_folder, "标签") { showTagPicker(rec) })
            if (canShare) {
                add(FileActionSheet.Action(R.drawable.ic_share, "分享文本") { shareText(rec) })
                add(FileActionSheet.Action(R.drawable.ic_doc, "分享文件") { shareFile(rec) })
            }
            add(FileActionSheet.Action(R.drawable.ic_delete, "删除", danger = true) { confirmDelete(rec) })
        }
        FileActionSheet(this, rec.name, sub, actions).show()
    }

    private fun shareText(rec: FileRecord) {
        try {
            val text = File(rec.storedPath).readText()
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(Intent.EXTRA_TITLE, rec.name)
            }, "分享文本"))
        } catch (e: Exception) { toast("分享失败:${e.message}") }
    }

    private fun shareFile(rec: FileRecord) {
        try {
            val shareDir = File(cacheDir, "share").apply { mkdirs() }
            val out = File(shareDir, rec.name)
            File(rec.storedPath).copyTo(out, overwrite = true)
            val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", out)
            val mime = when (rec.type) {
                FileType.MARKDOWN -> "text/markdown"; FileType.HTML -> "text/html"; else -> "*/*"
            }
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "分享文件"))
        } catch (e: Exception) { toast("分享失败:${e.message}") }
    }

    private fun showTagPicker(rec: FileRecord) {
        TagPickerSheet(
            ctx = this,
            fileName = rec.name,
            allTags = { LibraryStore.allTags(this) },
            fileTags = { LibraryStore.find(this, rec.id)?.tags ?: emptyList() },
            onToggle = { tag -> LibraryStore.toggleTag(this, rec.id, tag) },
            onCreate = { tag -> LibraryStore.toggleTag(this, rec.id, tag) },
            onDone = { renderList() }
        ).show()
    }

    private fun showRenameDialog(rec: FileRecord) {
        val view = layoutInflater.inflate(R.layout.dialog_rename, null)
        val input = view.findViewById<TextInputEditText>(R.id.renameInput)
        input.setText(rec.name)
        input.setSelection(input.text?.length ?: 0)
        MaterialAlertDialogBuilder(this)
            .setTitle("重命名")
            .setView(view)
            .setPositiveButton("确定") { _, _ ->
                val n = input.text.toString().trim()
                if (n.isNotEmpty()) { LibraryStore.rename(this, rec.id, n); renderList() }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun confirmDelete(rec: FileRecord) {
        MaterialAlertDialogBuilder(this)
            .setTitle("删除「${rec.name}」?")
            .setMessage("会从列表移除并删除本地副本(手机里的原文件不受影响)。")
            .setPositiveButton("删除") { _, _ -> LibraryStore.delete(this, rec.id); renderList() }
            .setNegativeButton("取消", null)
            .show()
    }

    // ---- 崩溃日志 ----
    private fun showLastCrashIfAny() {
        val f = File(filesDir, FolioApp.CRASH_FILE)
        if (!f.exists()) return
        val text = try { f.readText() } catch (e: Exception) { "读取崩溃日志失败:${e.message}" }
        f.delete()
        MaterialAlertDialogBuilder(this)
            .setTitle("上次崩溃日志(请截图发我)")
            .setMessage(text)
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
