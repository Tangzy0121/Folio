package com.folio.reader.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.color.MaterialColors
import com.folio.reader.reader.CodeStyle
import com.folio.reader.reader.CodeStyles
import com.folio.reader.reader.ReaderPrefs
import com.folio.reader.reader.ReaderThemes

/**
 * 「Aa」阅读设置面板:背景主题 / 字体 / 字号。
 * 颜色全部取自当前 Material 主题(colorOnSurface 等),随 APP 日夜自动适配——深色下自动浅字,不写死。
 */
class ReaderSettingsSheet(private val ctx: Context, private val onApply: () -> Unit) {

    private val accent = 0xFF2E7D6B.toInt()
    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    private fun attr(a: Int, def: Int) = MaterialColors.getColor(ctx, a, def)
    private val cOnSurface by lazy { attr(com.google.android.material.R.attr.colorOnSurface, 0xFF23272E.toInt()) }
    private val cOnSurfaceVar by lazy { attr(com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF6B7178.toInt()) }
    private val cSurface by lazy { attr(com.google.android.material.R.attr.colorSurface, Color.WHITE) }
    private val cSurfaceVar by lazy { attr(com.google.android.material.R.attr.colorSurfaceVariant, 0xFFEEEDE8.toInt()) }
    private val cOutline by lazy { attr(com.google.android.material.R.attr.colorOutline, 0x33888888) }

    fun show() {
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(22))
        }
        root.addView(label("背景主题"))
        root.addView(buildThemes())
        root.addView(label("字体"))
        root.addView(buildFonts())
        root.addView(label("字号"))
        root.addView(buildSize())
        root.addView(label("代码配色"))
        root.addView(buildCodeStyles())
        AdaptiveSheet.create(ctx, root).show()
    }

    private fun label(text: String) = TextView(ctx).apply {
        this.text = text
        setTextColor(cOnSurfaceVar)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(dp(2), dp(14), 0, dp(8))
    }

    private fun buildThemes(): View {
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val swatches = mutableListOf<Pair<String, View>>()
        fun refresh() {
            val cur = ReaderPrefs.themeKey(ctx)
            swatches.forEach { (key, v) ->
                val t = ReaderThemes.byKey(key)
                v.background = swatchDrawable(t.solid, t.gradient, key == cur)
            }
        }
        ReaderThemes.ALL.forEach { t ->
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, 0, dp(12), 0)
            }
            val sw = View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
                background = swatchDrawable(t.solid, t.gradient, false)
            }
            col.addView(sw)
            col.addView(TextView(ctx).apply {
                text = t.name
                setTextColor(cOnSurfaceVar)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, 0)
            })
            col.setOnClickListener { ReaderPrefs.setThemeKey(ctx, t.key); refresh(); onApply() }
            swatches.add(t.key to sw)
            row.addView(col)
        }
        refresh()
        return HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false; addView(row) }
    }

    private fun swatchDrawable(solid: Int?, gradient: IntArray?, selected: Boolean) =
        GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            if (gradient != null) {
                colors = gradient; orientation = GradientDrawable.Orientation.TOP_BOTTOM
            } else setColor(solid ?: Color.WHITE)
            setStroke(dp(if (selected) 3 else 1), if (selected) accent else cOutline)
        }

    private fun buildCodeStyles(): View {
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val swatches = mutableListOf<Pair<String, View>>()
        fun refresh() {
            val cur = ReaderPrefs.codeStyleKey(ctx)
            swatches.forEach { (key, v) ->
                val cs = CodeStyles.PICKABLE.first { it.key == key }
                v.background = codeSwatch(cs, key == cur)
            }
        }
        CodeStyles.PICKABLE.forEach { cs ->
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, 0, dp(12), 0)
            }
            val sw = View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(dp(52), dp(40))
                background = codeSwatch(cs, false)
            }
            col.addView(sw)
            col.addView(TextView(ctx).apply {
                text = cs.name
                setTextColor(cOnSurfaceVar)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, 0)
            })
            col.setOnClickListener { ReaderPrefs.setCodeStyleKey(ctx, cs.key); refresh(); onApply() }
            swatches.add(cs.key to sw)
            row.addView(col)
        }
        refresh()
        return HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false; addView(row) }
    }

    /** 代码配色 swatch:底色 = 代码块背景,中央一道关键字色条作为提示。 */
    private fun codeSwatch(cs: CodeStyle, selected: Boolean) = GradientDrawable().apply {
        cornerRadius = dp(10).toFloat()
        setColor(cs.bg)
        setStroke(dp(if (selected) 3 else 1), if (selected) accent else cOutline)
    }

    private fun buildFonts(): View {
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val fonts = listOf(
            ReaderPrefs.FONT_SANS to "无衬线",
            ReaderPrefs.FONT_SERIF to "衬线",
            ReaderPrefs.FONT_MONO to "等宽"
        )
        val btns = mutableListOf<Pair<String, TextView>>()
        fun refresh() {
            val cur = ReaderPrefs.fontKey(ctx)
            btns.forEach { (key, b) -> b.background = pillDrawable(key == cur) }
        }
        fonts.forEach { (key, name) ->
            val b = TextView(ctx).apply {
                text = name
                gravity = Gravity.CENTER
                setTextColor(cOnSurface)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = ReaderPrefs.typeface(key)
                setPadding(0, dp(10), 0, dp(10))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { marginEnd = dp(8) }
                setOnClickListener { ReaderPrefs.setFontKey(ctx, key); refresh(); onApply() }
            }
            btns.add(key to b)
            row.addView(b)
        }
        refresh()
        return row
    }

    private fun buildSize(): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        val sizeLabel = TextView(ctx).apply {
            gravity = Gravity.CENTER
            setTextColor(cOnSurface)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        fun updateLabel() { sizeLabel.text = "${ReaderPrefs.sizeSp(ctx)} sp" }
        fun step(delta: Int) { ReaderPrefs.setSizeSp(ctx, ReaderPrefs.sizeSp(ctx) + delta); updateLabel(); onApply() }
        row.addView(sizeBtn("A −") { step(-1) })
        row.addView(sizeLabel)
        row.addView(sizeBtn("A ＋") { step(1) })
        updateLabel()
        return row
    }

    private fun sizeBtn(text: String, onClick: () -> Unit) = TextView(ctx).apply {
        this.text = text
        gravity = Gravity.CENTER
        setTextColor(cOnSurface)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        background = pillDrawable(false)
        layoutParams = LinearLayout.LayoutParams(dp(72), dp(46))
        setOnClickListener { onClick() }
    }

    private fun pillDrawable(selected: Boolean) = GradientDrawable().apply {
        cornerRadius = dp(12).toFloat()
        setColor(if (selected) cSurface else cSurfaceVar)
        setStroke(dp(if (selected) 2 else 1), if (selected) cOnSurface else cOutline)
    }
}
