package com.folio.reader.reader

import android.content.Context
import androidx.webkit.WebViewAssetLoader
import java.io.File

/**
 * 把解压目录映射到虚拟域名,WebView 通过它加载 ZIP 内的本地资源(图片/CSS/JS),
 * 不需放开 file:// 访问,相对路径正常解析。
 */
object AssetLoaderFactory {

    const val BASE = "https://appassets.androidplatform.net/zip/"

    fun create(context: Context, dir: File): WebViewAssetLoader =
        WebViewAssetLoader.Builder()
            .addPathHandler("/zip/", WebViewAssetLoader.InternalStoragePathHandler(context, dir))
            .build()
}
