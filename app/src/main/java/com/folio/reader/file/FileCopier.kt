package com.folio.reader.file

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException

/** 复制到持久库后的文件信息。id 作为去重键。 */
data class LibraryFile(val id: String, val path: String, val size: Long, val name: String)

/**
 * 把 content:// / file:// 内容取到 App 私有目录。
 * 渲染用的文件存到 **filesDir/library/files**(持久,供最近/收藏长期再打开),不再用 cache。
 */
object FileCopier {

    /** 查显示名:content 走 ContentResolver DISPLAY_NAME,file 取最后路径段。 */
    fun queryName(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        context.contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
        )?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && !c.isNull(idx)) return c.getString(idx)
            }
        }
        return uri.lastPathSegment
    }

    /** 读前 n 字节用于类型嗅探。 */
    fun peekHead(context: Context, uri: Uri, n: Int = 64): ByteArray? = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buf = ByteArray(n)
            val read = input.read(buf)
            if (read <= 0) ByteArray(0) else buf.copyOf(read)
        }
    } catch (e: Exception) {
        null
    }

    /** 流式复制到 filesDir/library/files/<hash>_<安全名>;返回去重 id + 持久路径。 */
    fun copyToLibrary(context: Context, uri: Uri, displayName: String): LibraryFile {
        val dir = File(context.filesDir, "library/files").apply { mkdirs() }
        val safe = sanitize(displayName)
        val tmp = File(dir, ".tmp_$safe")
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("无法读取文件流:$uri")
        input.use { src -> tmp.outputStream().use { src.copyTo(it) } }

        val size = tmp.length()
        val id = "$displayName|$size"
        val finalFile = File(dir, "${kotlin.math.abs(id.hashCode())}_$safe")
        if (finalFile.exists()) finalFile.delete()
        if (!tmp.renameTo(finalFile)) {
            tmp.copyTo(finalFile, overwrite = true)
            tmp.delete()
        }
        return LibraryFile(id, finalFile.absolutePath, size, displayName)
    }

    /** content uri 取原始输入流(供 ZIP 解压用,子阶段 C)。 */
    fun openStream(context: Context, uri: Uri) = context.contentResolver.openInputStream(uri)

    private fun sanitize(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "document" }
}
