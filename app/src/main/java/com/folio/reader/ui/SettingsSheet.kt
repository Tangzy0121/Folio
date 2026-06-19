package com.folio.reader.ui

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.color.MaterialColors
import com.folio.reader.R
import com.folio.reader.data.AppPrefs

/** 设置(Material 底部抽屉):外观(跟随系统/浅色/深色)+ 是否在卡片显示标签。 */
class SettingsSheet(
    private val ctx: Context,
    private val onChanged: () -> Unit
) {
    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
    private fun attr(a: Int, def: Int) = MaterialColors.getColor(ctx, a, def)
    private val onSurface by lazy { attr(com.google.android.material.R.attr.colorOnSurface, 0xFF23272E.toInt()) }
    private val onSurfaceVar by lazy { attr(com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF6B7178.toInt()) }
    private val outline by lazy { attr(com.google.android.material.R.attr.colorOutline, 0x22888888) }
    private val accent = 0xFF4FA08B.toInt()

    private lateinit var box: LinearLayout

    fun show() {
        val dialog = BottomSheetDialog(ctx)
        box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(14))
        }
        populate(dialog)
        dialog.setContentView(box)
        dialog.show()
    }

    private fun populate(dialog: BottomSheetDialog) {
        box.removeAllViews()
        box.addView(label("外观"))
        val cur = AppPrefs.nightMode(ctx)
        listOf(
            "跟随系统" to AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            "浅色" to AppCompatDelegate.MODE_NIGHT_NO,
            "深色" to AppCompatDelegate.MODE_NIGHT_YES
        ).forEach { (name, mode) ->
            box.addView(row(name, checked = cur == mode) {
                AppPrefs.setNightMode(ctx, mode)  // 自动 recreate
                dialog.dismiss()
            })
        }
        box.addView(divider())
        box.addView(label("标签"))
        box.addView(row("在卡片上显示标签", checked = AppPrefs.showTags(ctx)) {
            AppPrefs.setShowTags(ctx, !AppPrefs.showTags(ctx))
            onChanged()
            populate(dialog)  // 原地刷新勾选
        })
    }

    private fun label(text: String) = TextView(ctx).apply {
        this.text = text
        setTextColor(onSurfaceVar)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(20), dp(12), dp(20), dp(6))
    }

    private fun row(text: String, checked: Boolean, onClick: () -> Unit): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(14), dp(20), dp(14))
            isClickable = true
            background = ripple()
            setOnClickListener { onClick() }
        }
        row.addView(TextView(ctx).apply {
            this.text = text
            setTextColor(onSurface)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        if (checked) {
            row.addView(ImageView(ctx).apply {
                setImageResource(R.drawable.ic_check)
                setColorFilter(accent)
                layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
            })
        }
        return row
    }

    private fun divider() = View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
            .apply { topMargin = dp(6); bottomMargin = dp(2) }
        setBackgroundColor(outline)
    }

    private fun ripple(): android.graphics.drawable.Drawable? {
        val tv = TypedValue()
        ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
        return if (tv.resourceId != 0) androidx.core.content.ContextCompat.getDrawable(ctx, tv.resourceId) else null
    }
}
