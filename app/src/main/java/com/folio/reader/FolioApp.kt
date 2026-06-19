package com.folio.reader

import android.app.Application
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * 全局崩溃兜底:把未捕获异常的堆栈写到内部文件,下次启动 MainActivity 时弹出来给用户截图。
 * 这样没有 adb 也能拿到崩溃原因。
 */
class FolioApp : Application() {

    override fun onCreate() {
        super.onCreate()
        com.folio.reader.data.AppPrefs.apply(this)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                File(filesDir, CRASH_FILE).writeText("thread=${thread.name}\n\n$sw")
            } catch (_: Throwable) {
                // 记录失败也不能再抛
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        const val CRASH_FILE = "last_crash.txt"
    }
}
