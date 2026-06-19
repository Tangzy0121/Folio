package com.folio.reader

import com.folio.reader.file.ZipExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipExtractorTest {

    private fun tmp(): File =
        File(System.getProperty("java.io.tmpdir"), "ziptest_${System.nanoTime()}").apply { mkdirs() }

    @Test
    fun extractsAndFindsIndex() {
        val base = tmp()
        val zip = File(base, "a.zip")
        ZipOutputStream(zip.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("index.html")); z.write("<html></html>".toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("img/a.txt")); z.write("x".toByteArray()); z.closeEntry()
        }
        val out = File(base, "out")
        val r = ZipExtractor.extract(zip, out)
        assertEquals("index.html", r.entry)
        assertTrue(File(out, "img/a.txt").exists())
    }

    @Test
    fun fallsBackToAnyHtml() {
        val base = tmp()
        val zip = File(base, "b.zip")
        ZipOutputStream(zip.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("pages/main.html")); z.write("<html></html>".toByteArray()); z.closeEntry()
        }
        val out = File(base, "out")
        val r = ZipExtractor.extract(zip, out)
        assertEquals("pages/main.html", r.entry)
    }

    @Test(expected = IOException::class)
    fun rejectsZipSlip() {
        val base = tmp()
        val zip = File(base, "evil.zip")
        ZipOutputStream(zip.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("../evil.txt")); z.write("pwn".toByteArray()); z.closeEntry()
        }
        ZipExtractor.extract(zip, File(base, "out"))
    }
}
