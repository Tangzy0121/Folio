package com.folio.reader.reader

import android.annotation.SuppressLint
import android.webkit.WebView

/**
 * WebView 安全基线:默认禁 JS、禁文件/内容访问、**始终阻断网络**(纯本地隐私,不申请 INTERNET 权限)。
 * JS 开关只切 javaScriptEnabled,网络永远不放开——这是即阅的隐私底线。
 */
object WebViewConfigurator {

    @SuppressLint("SetJavaScriptEnabled")
    fun applySecureDefaults(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            blockNetworkLoads = true          // 永远不联网
            builtInZoomControls = true        // 双指缩放
            displayZoomControls = false
            setSupportZoom(true)
            useWideViewPort = true
            loadWithOverviewMode = true
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun setJsEnabled(webView: WebView, enabled: Boolean) {
        webView.settings.javaScriptEnabled = enabled
    }
}
