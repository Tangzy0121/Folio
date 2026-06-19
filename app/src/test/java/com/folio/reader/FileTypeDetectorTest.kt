package com.folio.reader

import com.folio.reader.file.FileType
import com.folio.reader.file.FileTypeDetector
import org.junit.Assert.assertEquals
import org.junit.Test

/** FileTypeDetector 纯逻辑单测(JVM host,无需设备)。 */
class FileTypeDetectorTest {

    @Test
    fun byExtension() {
        assertEquals(FileType.MARKDOWN, FileTypeDetector.byName("a.md"))
        assertEquals(FileType.MARKDOWN, FileTypeDetector.byName("README.MARKDOWN"))
        assertEquals(FileType.HTML, FileTypeDetector.byName("x.html"))
        assertEquals(FileType.HTML, FileTypeDetector.byName("y.htm"))
        assertEquals(FileType.ZIP, FileTypeDetector.byName("site.zip"))
        assertEquals(FileType.UNSUPPORTED, FileTypeDetector.byName("note.txt"))
        assertEquals(FileType.UNSUPPORTED, FileTypeDetector.byName(null))
    }

    @Test
    fun sniffZipMagic() {
        val zip = byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4, 0, 0)
        assertEquals(FileType.ZIP, FileTypeDetector.sniff(zip))
    }

    @Test
    fun sniffHtml() {
        assertEquals(FileType.HTML, FileTypeDetector.sniff("<!DOCTYPE html><html>".toByteArray()))
        assertEquals(FileType.HTML, FileTypeDetector.sniff("  <html lang=\"en\">".toByteArray()))
    }

    @Test
    fun sniffFallbackMarkdown() {
        assertEquals(FileType.MARKDOWN, FileTypeDetector.sniff("# Title\nhello".toByteArray()))
    }

    @Test
    fun detectPrefersExtension() {
        // 扩展名是 .md,即便内容像 html 也按 md
        assertEquals(FileType.MARKDOWN, FileTypeDetector.detect("a.md", "<html>".toByteArray()))
    }

    @Test
    fun detectFallsBackToHead() {
        assertEquals(FileType.HTML, FileTypeDetector.detect(null, "<html>".toByteArray()))
        assertEquals(FileType.HTML, FileTypeDetector.detect("noext", "<html>".toByteArray()))
        assertEquals(FileType.UNSUPPORTED, FileTypeDetector.detect("noext", null))
    }
}
