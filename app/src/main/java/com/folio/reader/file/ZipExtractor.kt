package com.folio.reader.file

import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream

/**
 * 解压 ZIP 网页包到目标目录,**防 Zip-Slip**(每个 entry 的规范路径必须落在目标目录内)。
 * 纯 java.io / java.util.zip,无 Android 依赖,可 JVM 单测。
 */
object ZipExtractor {

    data class Result(val dir: File, val entry: String)

    fun extract(zip: File, destDir: File): Result {
        if (destDir.exists()) destDir.deleteRecursively()
        destDir.mkdirs()
        val base = destDir.canonicalPath

        ZipInputStream(BufferedInputStream(zip.inputStream())).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                val out = File(destDir, e.name)
                val canonical = out.canonicalPath
                if (canonical != base && !canonical.startsWith(base + File.separator)) {
                    throw IOException("非法路径(Zip-Slip):${e.name}")
                }
                if (e.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zis.copyTo(it) }
                }
                zis.closeEntry()
                e = zis.nextEntry
            }
        }

        val entry = findEntry(destDir) ?: throw IOException("ZIP 里没有找到 HTML 入口页")
        return Result(destDir, entry)
    }

    /** 入口页:根 index.html 优先;否则取「最浅 + 名为 index」的 *.html。返回相对路径(/ 分隔)。 */
    private fun findEntry(destDir: File): String? {
        val rootIndex = File(destDir, "index.html")
        if (rootIndex.isFile) return "index.html"

        val htmls = destDir.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in setOf("html", "htm") }
            .toList()
        if (htmls.isEmpty()) return null

        val best = htmls.minByOrNull { f ->
            val rel = f.relativeTo(destDir).path
            val depth = rel.count { it == File.separatorChar }
            val indexRank = if (f.nameWithoutExtension.equals("index", true)) 0 else 1
            depth * 10 + indexRank
        }!!
        return best.relativeTo(destDir).path.replace(File.separatorChar, '/')
    }
}
