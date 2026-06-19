package com.folio.reader.file

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

/** 从外部「打开方式 / 分享」intent 中取出文件 uri。 */
object FileIntentHandler {

    fun extractUri(intent: Intent): Uri? = when (intent.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND ->
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        else -> null
    }

    fun isFileIntent(intent: Intent?): Boolean =
        intent?.action == Intent.ACTION_VIEW || intent?.action == Intent.ACTION_SEND
}
