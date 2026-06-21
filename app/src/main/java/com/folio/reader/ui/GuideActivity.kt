package com.folio.reader.ui

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.folio.reader.databinding.ActivityGuideBinding

/**
 * 使用指引页:WebView 加载本地 assets/guide.html(9 步高亮热点图文指引)。
 * - guide.html 是可交互的引导,JS 通过 `Android.close()` 在"开始使用/跳过"时关闭本页。
 * - 跟随系统深浅:夜间注入 body.dark。
 */
class GuideActivity : AppCompatActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityGuideBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val web = binding.guideWeb
        web.settings.javaScriptEnabled = true   // 本地可信资源,仅指引交互用
        web.settings.domStorageEnabled = true
        web.setBackgroundColor(0x00000000)
        web.addJavascriptInterface(GuideBridge(), "Android")

        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                if (night) view.evaluateJavascript(
                    "document.body.classList.add('dark');" +
                        "var b=document.getElementById('themeBtn'); if(b) b.textContent='☀️ 浅色';",
                    null
                )
            }
        }
        web.loadUrl("file:///android_asset/guide.html")
    }

    private inner class GuideBridge {
        @JavascriptInterface
        fun close() { runOnUiThread { finish() } }
    }
}
