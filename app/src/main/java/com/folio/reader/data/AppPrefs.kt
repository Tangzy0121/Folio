package com.folio.reader.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/** 全局 APP 偏好:日夜模式(跟随系统/浅/深)。 */
object AppPrefs {

    private const val PREF = "app_prefs"
    private const val K_NIGHT = "night_mode"
    private const val K_SHOW_TAGS = "show_tags"
    private const val K_SEEN_GUIDE = "seen_guide"

    fun nightMode(ctx: Context): Int =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getInt(K_NIGHT, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

    fun setNightMode(ctx: Context, mode: Int) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putInt(K_NIGHT, mode).apply()
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    fun showTags(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(K_SHOW_TAGS, true)

    fun setShowTags(ctx: Context, show: Boolean) =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(K_SHOW_TAGS, show).apply()

    /** 首次启动是否已弹过欢迎/指引(只弹一次)。 */
    fun seenGuide(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(K_SEEN_GUIDE, false)

    fun setSeenGuide(ctx: Context, seen: Boolean) =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(K_SEEN_GUIDE, seen).apply()

    fun apply(ctx: Context) {
        AppCompatDelegate.setDefaultNightMode(nightMode(ctx))
    }
}
