package com.folio.reader.ui

import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewFeature
import com.folio.reader.databinding.ActivityHtmlReaderBinding
import com.folio.reader.reader.AssetLoaderFactory
import com.folio.reader.reader.ReaderPrefs
import com.folio.reader.reader.ReaderThemes
import com.folio.reader.reader.WebViewConfigurator
import java.io.File

/**
 * HTML 阅读页(单文件 / ZIP 两模式)+ 电子书主题。
 * 单文件:注入 CSS(背景/文字色/字体)+ textZoom(字号)。ZIP:setBackgroundColor + textZoom + 深色主题用算法暗化。
 * 默认禁 JS、不联网;顶栏可手动开 JS。
 */
class HtmlReaderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHtmlReaderBinding
    private var jsEnabled = false
    private var isZip = false
    private var html: String = ""
    private var entryUrl: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHtmlReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.titleText.text = intent.getStringExtra(EXTRA_NAME) ?: "HTML"

        WebViewConfigurator.applySecureDefaults(binding.webView)

        val zipDir = intent.getStringExtra(EXTRA_ZIP_DIR)
        val entry = intent.getStringExtra(EXTRA_ENTRY)
        if (zipDir != null && entry != null) {
            if (!setupZip(zipDir, entry)) return
        } else {
            if (!setupSingle()) return
        }

        updateJsUi()
        applyReaderStyle()
        binding.btnAa.setOnClickListener { ReaderSettingsSheet(this) { applyReaderStyle() }.show() }
        binding.btnJsToggle.setOnClickListener {
            jsEnabled = !jsEnabled
            WebViewConfigurator.setJsEnabled(binding.webView, jsEnabled)
            updateJsUi()
            render()
            Toast.makeText(this, if (jsEnabled) "已开启 JavaScript" else "已关闭 JavaScript", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSingle(): Boolean {
        val path = intent.getStringExtra(EXTRA_PATH)
        if (path.isNullOrEmpty()) { fail("文件路径丢失"); return false }
        html = try { File(path).readText() } catch (e: Exception) { fail("读取失败:${e.message}"); return false }
        return true
    }

    private fun setupZip(zipDir: String, entry: String): Boolean {
        val dir = File(zipDir)
        if (!dir.isDirectory) { fail("网页包已不在"); return false }
        isZip = true
        entryUrl = AssetLoaderFactory.BASE + entry
        val loader: WebViewAssetLoader = AssetLoaderFactory.create(this, dir)
        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url)
        }
        return true
    }

    // ---- 主题(背景/字色/字体/字号)----
    private fun applyReaderStyle() {
        val t = ReaderThemes.byKey(ReaderPrefs.themeKey(this))
        binding.root.setBackgroundColor(t.bgColor)
        binding.titleText.setTextColor(t.textColor)
        binding.btnBack.setColorFilter(t.textColor)
        binding.btnAa.setColorFilter(t.textColor)
        binding.shield.setColorFilter(t.textColor)
        binding.btnJsToggle.setTextColor(t.textColor)
        binding.webView.setBackgroundColor(t.bgColor)

        // 字号 → textZoom(17sp 基准 = 100%)
        binding.webView.settings.textZoom = (ReaderPrefs.sizeSp(this) * 100 / ReaderPrefs.DEFAULT_SIZE).coerceIn(70, 200)
        // ZIP 页面无法注入 CSS,深色主题用算法暗化兜底
        if (isZip && WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(binding.webView.settings, t.isDark)
        }
        window.statusBarColor = t.bgColor
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !t.isDark
        render()
    }

    private fun render() {
        if (isZip) {
            binding.webView.loadUrl(entryUrl)
        } else {
            binding.webView.loadDataWithBaseURL(null, themedHtml(), "text/html", "utf-8", null)
        }
    }

    /** 单文件:把主题作为 CSS 注入到 html 头部。 */
    private fun themedHtml(): String {
        val t = ReaderThemes.byKey(ReaderPrefs.themeKey(this))
        val bg = if (t.gradient != null)
            "linear-gradient(180deg, ${hex(t.gradient[0])}, ${hex(t.gradient[1])})"
        else hex(t.solid!!)
        val ff = ReaderPrefs.cssFontFamily(ReaderPrefs.fontKey(this))
        val style = "<style>html,body{background:$bg !important;color:${hex(t.textColor)} !important;" +
            "font-family:$ff !important;} a{color:#4FA08B !important;}</style>"
        return style + html
    }

    private fun hex(c: Int) = String.format("#%06X", 0xFFFFFF and c)

    private fun updateJsUi() {
        binding.btnJsToggle.text = if (jsEnabled) "JS 开" else "JS 关"
    }

    private fun fail(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onDestroy() {
        binding.webView.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PATH = "extra_path"
        const val EXTRA_NAME = "extra_name"
        const val EXTRA_ID = "extra_id"
        const val EXTRA_ZIP_DIR = "extra_zip_dir"
        const val EXTRA_ENTRY = "extra_entry"
    }
}
