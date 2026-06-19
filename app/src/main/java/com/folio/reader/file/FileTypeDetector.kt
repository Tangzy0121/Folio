package com.folio.reader.file

/** 支持的文件类型。 */
enum class FileType { MARKDOWN, HTML, ZIP, UNSUPPORTED }

/**
 * 判定文件类型:优先按扩展名;扩展名缺失/不可信时用文件头嗅探。
 * 纯逻辑、无 Android 依赖,可在 JVM 上单测。
 */
object FileTypeDetector {

    private val MD_EXT = setOf("md", "markdown", "mdown", "mkd")
    private val HTML_EXT = setOf("html", "htm", "xhtml")
    private val ZIP_EXT = setOf("zip")

    fun byName(name: String?): FileType {
        val ext = name?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return when (ext) {
            in MD_EXT -> FileType.MARKDOWN
            in HTML_EXT -> FileType.HTML
            in ZIP_EXT -> FileType.ZIP
            else -> FileType.UNSUPPORTED
        }
    }

    /** 看文件头若干字节做嗅探:ZIP 魔数 PK\x03\x04;HTML 标签;否则当作纯文本 MD。 */
    fun sniff(head: ByteArray): FileType {
        if (head.size >= 4 &&
            head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() &&
            head[2] == 3.toByte() && head[3] == 4.toByte()
        ) return FileType.ZIP

        val text = String(head, Charsets.UTF_8).trimStart().lowercase()
        if (text.startsWith("<!doctype html") || text.startsWith("<html") ||
            text.contains("<head") || text.contains("<body")
        ) return FileType.HTML

        return FileType.MARKDOWN
    }

    /** 综合判定:扩展名能定就用扩展名,否则用文件头。 */
    fun detect(name: String?, head: ByteArray?): FileType {
        val byExt = byName(name)
        if (byExt != FileType.UNSUPPORTED) return byExt
        return if (head != null && head.isNotEmpty()) sniff(head) else FileType.UNSUPPORTED
    }
}
