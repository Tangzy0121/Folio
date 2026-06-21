package com.folio.reader.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.folio.reader.R
import com.folio.reader.data.LibraryStore
import com.folio.reader.databinding.ActivityPdfReaderBinding
import java.io.File
import kotlin.math.roundToInt

/**
 * PDF 阅读页:系统 PdfRenderer 把每页渲成位图,竖排 RecyclerView 浏览;点某页用 ImageZoomDialog 捏合放大。
 * 进度管理(电子书重点):按"当前可见页 / 总页"存为 progress 分数,重开恢复到该页;底部常驻页码指示器。
 * 纯本地零依赖。实事求是:按页看图,不支持选中文字/搜索/重排(那需重型 PDF 引擎)。
 */
class PdfReaderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPdfReaderBinding
    private lateinit var lm: LinearLayoutManager
    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private var recordId: String? = null
    private var pageCount = 0
    private var restored = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPdfReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnBack.setOnClickListener { finish() }
        binding.titleText.text = intent.getStringExtra(EXTRA_NAME) ?: "PDF"
        recordId = intent.getStringExtra(EXTRA_ID)

        val path = intent.getStringExtra(EXTRA_PATH)
        if (path.isNullOrEmpty()) { fail("文件路径丢失"); return }
        val r = try {
            val f = ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
            pfd = f
            PdfRenderer(f).also { renderer = it }
        } catch (e: Exception) {
            fail("打开 PDF 失败:${e.message}"); return
        }
        pageCount = r.pageCount

        val sidePad = binding.recyclerPages.paddingStart + binding.recyclerPages.paddingEnd
        val itemMargin = (12 * resources.displayMetrics.density).toInt()
        val target = (resources.displayMetrics.widthPixels - sidePad - itemMargin)
            .coerceIn(dp(120), dp(900))

        lm = LinearLayoutManager(this)
        binding.recyclerPages.layoutManager = lm
        binding.recyclerPages.adapter = PageAdapter(r, target)

        updateIndicator(0)
        binding.recyclerPages.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                updateIndicator(lm.findFirstVisibleItemPosition())
            }
        })
        // 恢复上次阅读到的页
        val frac = recordId?.let { LibraryStore.find(this, it)?.progress } ?: 0f
        if (frac > 0f && pageCount > 1) {
            val page = (frac * (pageCount - 1)).roundToInt().coerceIn(0, pageCount - 1)
            binding.recyclerPages.post {
                lm.scrollToPositionWithOffset(page, 0)
                updateIndicator(page)
                restored = true
            }
        } else restored = true
    }

    override fun onPause() {
        super.onPause()
        saveProgress()
    }

    private fun saveProgress() {
        val id = recordId ?: return
        if (!restored || pageCount <= 1) return
        val pos = lm.findFirstVisibleItemPosition().coerceAtLeast(0)
        LibraryStore.updateProgress(this, id, pos.toFloat() / (pageCount - 1))
    }

    private fun updateIndicator(pos: Int) {
        val p = (pos.coerceAtLeast(0)) + 1
        binding.pageIndicator.text = "$p / $pageCount"
    }

    override fun onDestroy() {
        super.onDestroy()
        try { renderer?.close() } catch (_: Exception) {}
        try { pfd?.close() } catch (_: Exception) {}
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun fail(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); finish()
    }

    private inner class PageAdapter(
        private val renderer: PdfRenderer,
        private val targetWidth: Int
    ) : RecyclerView.Adapter<PageAdapter.Holder>() {

        private val lock = Any()
        private val cache = object : LruCache<Int, Bitmap>(
            (Runtime.getRuntime().maxMemory() / 8).toInt().coerceAtLeast(8 * 1024 * 1024)
        ) {
            override fun sizeOf(key: Int, value: Bitmap) = value.byteCount
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_pdf_page, parent, false))

        override fun getItemCount() = renderer.pageCount

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val bmp = renderPage(position)
            holder.image.setImageBitmap(bmp)
            holder.image.setOnClickListener {
                ImageZoomDialog.show(this@PdfReaderActivity, BitmapDrawable(resources, bmp))
            }
        }

        private fun renderPage(pos: Int): Bitmap {
            cache.get(pos)?.let { return it }
            synchronized(lock) {
                cache.get(pos)?.let { return it }
                val page = renderer.openPage(pos)
                val h = (targetWidth.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(targetWidth, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                cache.put(pos, bmp)
                return bmp
            }
        }

        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val image: ImageView = view.findViewById(R.id.pageImage)
        }
    }

    companion object {
        const val EXTRA_PATH = "extra_path"
        const val EXTRA_NAME = "extra_name"
        const val EXTRA_ID = "extra_id"
    }
}
