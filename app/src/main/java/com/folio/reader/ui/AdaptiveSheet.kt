package com.folio.reader.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.folio.reader.R
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * 自适应弹层:
 * - 窄屏(手机)= Material 底部抽屉 BottomSheetDialog(沿用旧体验)。
 * - 宽屏(平板)= 屏幕居中的模态卡片弹窗(点即现,不用拖) —— 修真机反馈「平板设置要拖上来」。
 *
 * 各 Sheet 把构建好的内容 View 交给 [create],拿回一个 [Dialog],照常 show()/dismiss()/setOnDismissListener。
 */
object AdaptiveSheet {

    fun create(ctx: Context, content: View): Dialog {
        val wide = ctx.resources.getBoolean(R.bool.folio_wide_screen)
        if (!wide) {
            return BottomSheetDialog(ctx).apply { setContentView(content) }
        }
        val density = ctx.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val card = FrameLayout(ctx).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(ContextCompat.getColor(ctx, R.color.jiyue_card))
            }
            clipToOutline = true
            addView(content, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        return Dialog(ctx).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(card)
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                val w = (ctx.resources.displayMetrics.widthPixels * 0.7f)
                    .toInt().coerceAtMost(dp(420))
                setLayout(w, ViewGroup.LayoutParams.WRAP_CONTENT)
                setGravity(Gravity.CENTER)
            }
        }
    }
}
