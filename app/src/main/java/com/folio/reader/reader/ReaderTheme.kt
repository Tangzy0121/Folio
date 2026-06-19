package com.folio.reader.reader

import androidx.core.graphics.ColorUtils

/**
 * 阅读主题:纯色或渐变背景 + 文字色。渐变用于「简约风景」类。
 * solid 与 gradient 二选一(gradient 优先)。
 */
data class ReaderTheme(
    val key: String,
    val name: String,
    val textColor: Int,
    val solid: Int? = null,
    val gradient: IntArray? = null
) {
    /** 用于背景色板/状态栏/webview 底色的代表色。 */
    val bgColor: Int get() = solid ?: gradient!![0]

    val isDark: Boolean get() = ColorUtils.calculateLuminance(bgColor) < 0.45

    override fun equals(other: Any?) = other is ReaderTheme && other.key == key
    override fun hashCode() = key.hashCode()
}

object ReaderThemes {
    val ALL: List<ReaderTheme> = listOf(
        ReaderTheme("paper", "纸白", 0xFF2B2B2B.toInt(), solid = 0xFFFBFAF7.toInt()),
        ReaderTheme("sepia", "米黄", 0xFF5B4636.toInt(), solid = 0xFFF3E9D2.toInt()),
        ReaderTheme("mint", "青墨", 0xFF27352E.toInt(), solid = 0xFFE2EDE7.toInt()),
        ReaderTheme("stone", "灰岩", 0xFF2D2F31.toInt(), solid = 0xFFE6E6E4.toInt()),
        ReaderTheme("night", "夜黑", 0xFFC9CCD1.toInt(), solid = 0xFF15171A.toInt()),
        ReaderTheme("bluenight", "深蓝夜", 0xFFC4D0DD.toInt(), solid = 0xFF0F1722.toInt()),
        ReaderTheme("dawn", "晨曦", 0xFF4A3B2C.toInt(), gradient = intArrayOf(0xFFFCEFE3.toInt(), 0xFFEAD3B8.toInt())),
        ReaderTheme("mountain", "远山", 0xFF2A3940.toInt(), gradient = intArrayOf(0xFFDCE7EC.toInt(), 0xFFAEC6CB.toInt())),
        ReaderTheme("bamboo", "竹林", 0xFF26352A.toInt(), gradient = intArrayOf(0xFFE7EFE2.toInt(), 0xFFB9D4B2.toInt()))
    )

    fun byKey(k: String?): ReaderTheme = ALL.firstOrNull { it.key == k } ?: ALL[0]
}
