package com.folio.reader.ui

import android.app.Dialog
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatImageView

/** 全屏图片查看:捏合缩放 + 拖动平移 + 双击放大/还原 + 单击关闭。零新依赖。 */
object ImageZoomDialog {

    fun show(ctx: Context, drawable: Drawable) {
        val iv = ZoomableImageView(ctx)
        if (drawable is BitmapDrawable) iv.setImageBitmap(drawable.bitmap) else iv.setImageDrawable(drawable)

        val root = FrameLayout(ctx).apply {
            setBackgroundColor(0xE6000000.toInt())
            addView(iv, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        val dialog = Dialog(ctx, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(root)
        iv.onTap = { dialog.dismiss() }
        dialog.show()
    }

    private class ZoomableImageView(context: Context) : AppCompatImageView(context) {
        private var scale = 1f
        var onTap: (() -> Unit)? = null

        private val scaleDetector = ScaleGestureDetector(context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(d: ScaleGestureDetector): Boolean {
                    scale = (scale * d.scaleFactor).coerceIn(1f, 5f)
                    scaleX = scale; scaleY = scale
                    if (scale == 1f) { translationX = 0f; translationY = 0f }
                    return true
                }
            })

        private val gestureDetector = GestureDetector(context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    onTap?.invoke(); return true
                }
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    scale = if (scale > 1f) 1f else 2.5f
                    scaleX = scale; scaleY = scale
                    if (scale == 1f) { translationX = 0f; translationY = 0f }
                    return true
                }
                override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                    if (scale > 1f) { translationX -= dx; translationY -= dy }
                    return true
                }
            })

        init { scaleType = ScaleType.FIT_CENTER }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            scaleDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
            return true
        }
    }
}
