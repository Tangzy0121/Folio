package com.folio.reader.reader

import android.content.Context
import android.graphics.Typeface

/** 阅读偏好:主题 / 字体 / 字号,存 SharedPreferences,全局共享(MD 与 HTML 阅读页通用)。 */
object ReaderPrefs {

    private const val PREF = "reader_prefs"
    private const val K_THEME = "theme"
    private const val K_FONT = "font"
    private const val K_SIZE = "size_sp"
    private const val K_CODE = "code_style"

    const val FONT_SANS = "sans"
    const val FONT_SERIF = "serif"
    const val FONT_MONO = "mono"

    const val MIN_SIZE = 13
    const val MAX_SIZE = 26
    const val DEFAULT_SIZE = 17

    private fun p(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun themeKey(ctx: Context): String = p(ctx).getString(K_THEME, "paper") ?: "paper"
    fun setThemeKey(ctx: Context, k: String) = p(ctx).edit().putString(K_THEME, k).apply()

    fun fontKey(ctx: Context): String = p(ctx).getString(K_FONT, FONT_SANS) ?: FONT_SANS
    fun setFontKey(ctx: Context, k: String) = p(ctx).edit().putString(K_FONT, k).apply()

    fun sizeSp(ctx: Context): Int = p(ctx).getInt(K_SIZE, DEFAULT_SIZE)
    fun setSizeSp(ctx: Context, sp: Int) =
        p(ctx).edit().putInt(K_SIZE, sp.coerceIn(MIN_SIZE, MAX_SIZE)).apply()

    fun codeStyleKey(ctx: Context): String = p(ctx).getString(K_CODE, "auto") ?: "auto"
    fun setCodeStyleKey(ctx: Context, k: String) = p(ctx).edit().putString(K_CODE, k).apply()

    fun typeface(key: String): Typeface = when (key) {
        FONT_SERIF -> Typeface.SERIF
        FONT_MONO -> Typeface.MONOSPACE
        else -> Typeface.SANS_SERIF
    }

    /** CSS font-family(给 WebView 用)。 */
    fun cssFontFamily(key: String): String = when (key) {
        FONT_SERIF -> "serif"
        FONT_MONO -> "monospace"
        else -> "sans-serif"
    }
}
