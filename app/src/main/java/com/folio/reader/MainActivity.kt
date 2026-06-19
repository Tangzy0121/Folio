package com.folio.reader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.Formatter
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.folio.reader.data.FileRecord
import com.folio.reader.data.LibraryStore
import com.folio.reader.databinding.ActivityMainBinding
import com.folio.reader.file.FileCopier
import com.folio.reader.file.FileIntentHandler
import com.folio.reader.file.FileType
import com.folio.reader.file.FileTypeDetector
import com.folio.reader.file.ZipExtractor
import com.folio.reader.ui.FileActionSheet
import com.folio.reader.ui.FileCardAdapter
import com.folio.reader.ui.HtmlReaderActivity
import com.folio.reader.ui.MdReaderActivity
import com.folio.reader.ui.SettingsSheet
import com.folio.reader.ui.TagManagerSheet
import com.folio.reader.ui.TagPickerSheet
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: FileCardAdapter
    private var currentTab = 0  // 0=最近, 1=收藏
    private val currentTags = linkedSetOf<String>()  // 空 = 全部;多选 AND 筛选

    private val openDoc = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { handlePickedUri(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = FileCardAdapter(
            onOpen = { openRecord(it) },
            onToggleFav = { LibraryStore.toggleFavorite(this, it.id); refreshList() },
            onLongPress = { showFileActions(it) }
        )
        binding.recyclerFiles.layoutManager = LinearLayoutManager(this)
        binding.recyclerFiles.adapter = adapter

        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) { currentTab = tab.position; refreshList() }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        binding.btnFavoritesEntry.setOnClickListener { binding.tabs.getTabAt(1)?.select() }
        binding.btnSettings.setOnClickListener { SettingsSheet(this) { refreshList() }.show() }
        binding.btnManageTags.setOnClickListener { showTagManager() }
        binding.btnOpen.setOnClickListener { openDoc.launch(arrayOf("*/*")) }

        handleIncomingIntent(intent)
        showLastCrashIfAny()
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun refreshList() {
        val base = if (currentTab == 1) LibraryStore.favorites(this) else LibraryStore.recent(this)
        val tags = LibraryStore.allTags(this)
        currentTags.retainAll(tags.toSet())  // 已删除的标签从筛选里剔除
        buildTagChips(tags)
        val list = if (currentTags.isEmpty()) base else base.filter { it.tags.containsAll(currentTags) }
        adapter.submit(list)
        val empty = list.isEmpty()
        binding.recyclerFiles.visibility = if (empty) View.GONE else View.VISIBLE
        if (!empty) binding.recyclerFiles.scheduleLayoutAnimation()
        binding.emptyView.visibility = if (empty) View.VISIBLE else View.GONE
        binding.emptyView.text = when {
            currentTags.isNotEmpty() -> "没有同时含 ${currentTags.joinToString("、") { "#$it" }} 的文件"
            currentTab == 1 -> "还没有收藏\n在文件卡片点 ☆ 收藏"
            else -> getString(R.string.empty_hint)
        }
    }

    private fun buildTagChips(tags: List<String>) {
        val cg = binding.chipGroups
        cg.removeAllViews()
        if (tags.isEmpty()) { binding.groupBar.visibility = View.GONE; return }
        binding.groupBar.visibility = View.VISIBLE
        // 「全部」chip:清空筛选
        cg.addView(com.google.android.material.chip.Chip(this).apply {
            text = "全部"
            isCheckable = true
            isChecked = currentTags.isEmpty()
            setOnClickListener { currentTags.clear(); refreshList() }
        })
        tags.forEach { tag ->
            cg.addView(com.google.android.material.chip.Chip(this).apply {
                text = tag
                isCheckable = true
                isChecked = currentTags.contains(tag)
                setOnClickListener {
                    if (!currentTags.add(tag)) currentTags.remove(tag)  // toggle
                    refreshList()
                }
            })
        }
    }

    private fun showTagManager() {
        TagManagerSheet(
            ctx = this,
            allTags = { LibraryStore.allTags(this) },
            selected = { currentTags },
            onToggleFilter = { t -> if (!currentTags.add(t)) currentTags.remove(t) },
            onRename = { old, new -> LibraryStore.renameTag(this, old, new) },
            onDelete = { t -> LibraryStore.deleteTag(this, t) },
            onChanged = { refreshList() }
        ).show()
    }

    // ---- 打开入口(SAF / 外部 intent)----

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
            FileType.MARKDOWN, FileType.HTML -> importAndOpen(uri, name, type)
            FileType.ZIP -> importAndOpenZip(uri, name)
            FileType.UNSUPPORTED -> toast("不支持的文件类型(目前仅 MD / HTML / ZIP)")
        }
    }

    private fun importAndOpen(uri: Uri, name: String, type: FileType) {
        try {
            val lib = FileCopier.copyToLibrary(this, uri, name)
            val existing = LibraryStore.find(this, lib.id)
            val rec = FileRecord(
                id = lib.id,
                name = existing?.name ?: lib.name,
                type = type,
                size = lib.size,
                storedPath = lib.path,
                entryPath = null,
                lastOpened = System.currentTimeMillis(),
                isFavorite = existing?.isFavorite ?: false,
                progress = existing?.progress ?: 0f
            )
            LibraryStore.upsert(this, rec)
            openRecord(rec)
        } catch (e: Exception) {
            toast("打开失败:${e.message}")
        }
    }

    private fun importAndOpenZip(uri: Uri, name: String) {
        try {
            val lib = FileCopier.copyToLibrary(this, uri, name)
            val extractDir = File(filesDir, "library/zip/${kotlin.math.abs(lib.id.hashCode())}")
            val result = ZipExtractor.extract(File(lib.path), extractDir)
            File(lib.path).delete() // 解压完删掉 zip 本体,只留解压目录
            val existing = LibraryStore.find(this, lib.id)
            val rec = FileRecord(
                id = lib.id,
                name = existing?.name ?: lib.name,
                type = FileType.ZIP,
                size = lib.size,
                storedPath = extractDir.absolutePath,
                entryPath = result.entry,
                lastOpened = System.currentTimeMillis(),
                isFavorite = existing?.isFavorite ?: false,
                progress = existing?.progress ?: 0f
            )
            LibraryStore.upsert(this, rec)
            openRecord(rec)
        } catch (e: Exception) {
            toast("打开 ZIP 失败:${e.message}")
        }
    }

    private fun openRecord(rec: FileRecord) {
        if (!File(rec.storedPath).exists()) {
            toast("文件已不在,已从列表移除")
            LibraryStore.delete(this, rec.id)
            refreshList()
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
            FileType.UNSUPPORTED -> toast("不支持的类型")
        }
    }

    // ---- 文件管理(长按)----

    private fun showFileActions(rec: FileRecord) {
        val canShare = rec.type == FileType.MARKDOWN || rec.type == FileType.HTML
        val typeLabel = when (rec.type) {
            FileType.MARKDOWN -> "Markdown"; FileType.HTML -> "HTML"
            FileType.ZIP -> "网页包"; FileType.UNSUPPORTED -> "文件"
        }
        val sub = "$typeLabel · ${Formatter.formatShortFileSize(this, rec.size)}"
        val actions = buildList {
            add(FileActionSheet.Action(
                if (rec.isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star,
                if (rec.isFavorite) "取消收藏" else "收藏"
            ) { LibraryStore.toggleFavorite(this@MainActivity, rec.id); refreshList() })
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
        } catch (e: Exception) {
            toast("分享失败:${e.message}")
        }
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
        } catch (e: Exception) {
            toast("分享失败:${e.message}")
        }
    }

    private fun showTagPicker(rec: FileRecord) {
        TagPickerSheet(
            ctx = this,
            fileName = rec.name,
            allTags = { LibraryStore.allTags(this) },
            fileTags = { LibraryStore.find(this, rec.id)?.tags ?: emptyList() },
            onToggle = { tag -> LibraryStore.toggleTag(this, rec.id, tag) },
            onCreate = { tag -> LibraryStore.toggleTag(this, rec.id, tag) },  // 新建即贴到该文件
            onDone = { refreshList() }
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
                if (n.isNotEmpty()) { LibraryStore.rename(this, rec.id, n); refreshList() }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun confirmDelete(rec: FileRecord) {
        MaterialAlertDialogBuilder(this)
            .setTitle("删除「${rec.name}」?")
            .setMessage("会从列表移除并删除本地副本(手机里的原文件不受影响)。")
            .setPositiveButton("删除") { _, _ -> LibraryStore.delete(this, rec.id); refreshList() }
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
