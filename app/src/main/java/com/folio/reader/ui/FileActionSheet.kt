package com.folio.reader.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.color.MaterialColors
import com.folio.reader.R

/** 文件操作面板(Material 底部抽屉,替代安卓原生列表弹窗)。颜色随主题适配。 */
class FileActionSheet(
    private val ctx: Context,
    private val title: String,
    private val subtitle: String,
    private val actions: List<Action>
) {
    data class Action(
        val iconRes: Int,
        val label: String,
        val danger: Boolean = false,
        val checked: Boolean = false,
        val onClick: () -> Unit
    )

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
    private fun attr(a: Int, def: Int) = MaterialColors.getColor(ctx, a, def)
    private val onSurface by lazy { attr(com.google.android.material.R.attr.colorOnSurface, 0xFF23272E.toInt()) }
    private val onSurfaceVar by lazy { attr(com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF6B7178.toInt()) }
    private val error by lazy { attr(com.google.android.material.R.attr.colorError, 0xFFB3261E.toInt()) }
    private val outline by lazy { attr(com.google.android.material.R.attr.colorOutline, 0x22888888) }

    fun show() {
        val dialog = BottomSheetDialog(ctx)
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, dp(12))
        }

        // header
        root.addView(TextView(ctx).apply {
            text = title
            setTextColor(onSurface)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            setPadding(dp(20), dp(8), dp(20), 0)
        })
        if (subtitle.isNotBlank()) {
            root.addView(TextView(ctx).apply {
                text = subtitle
                setTextColor(onSurfaceVar)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(dp(20), dp(2), dp(20), dp(10))
            })
        } else {
            root.addView(View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(8))
            })
        }
        root.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
            setBackgroundColor(outline)
        })

        actions.forEach { a ->
            root.addView(actionRow(a, dialog))
        }

        dialog.setContentView(root)
        dialog.show()
    }

    private fun actionRow(a: Action, dialog: BottomSheetDialog): View {
        val color = if (a.danger) error else onSurface
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(15), dp(20), dp(15))
            isClickable = true
            background = rippleBg()
            setOnClickListener { dialog.dismiss(); a.onClick() }
        }
        if (a.iconRes != 0) {
            row.addView(ImageView(ctx).apply {
                setImageResource(a.iconRes)
                setColorFilter(color)
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
            })
        }
        row.addView(TextView(ctx).apply {
            text = a.label
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(if (a.iconRes != 0) dp(16) else 0, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        if (a.checked) {
            row.addView(ImageView(ctx).apply {
                setImageResource(R.drawable.ic_check)
                setColorFilter(0xFF4FA08B.toInt())
                layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
            })
        }
        return row
    }

    private fun rippleBg(): android.graphics.drawable.Drawable? {
        val tv = TypedValue()
        ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
        return if (tv.resourceId != 0) androidx.core.content.ContextCompat.getDrawable(ctx, tv.resourceId)
        else null
    }
}
