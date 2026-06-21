package com.folio.reader.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatImageView

/**
 * 全屏图片查看:捏合缩放 + 拖动平移 + 双击放大/还原 + 单击关闭。零新依赖。
 *
 * 缩放用 imageMatrix 以**手指焦点**为锚点(postScale(focusX,focusY)),平移后做边界约束。
 * 不用 view 的 scaleX/scaleY(那绕视图中心缩放,双指一动就飘抖)。
 */
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
        var onTap: (() -> Unit)? = null

        private val matrix = Matrix()      // 当前显示矩阵
        private val baseMatrix = Matrix()  // FIT_CENTER 基准(缩放=1 的归位状态)
        private val tmpValues = FloatArray(9)
        private val drawRect = RectF()
        private var ready = false

        private val maxScale = 5f

        init { scaleType = ScaleType.MATRIX }

        // ---- 基准矩阵:把图按 FIT_CENTER 铺进 view ----
        private fun setupBase() {
            val d = drawable ?: return
            val vw = width.toFloat(); val vh = height.toFloat()
            val iw = d.intrinsicWidth.toFloat(); val ih = d.intrinsicHeight.toFloat()
            if (vw <= 0 || vh <= 0 || iw <= 0 || ih <= 0) return
            val s = minOf(vw / iw, vh / ih)
            val dx = (vw - iw * s) / 2f
            val dy = (vh - ih * s) / 2f
            baseMatrix.reset()
            baseMatrix.postScale(s, s)
            baseMatrix.postTranslate(dx, dy)
            matrix.set(baseMatrix)
            imageMatrix = matrix
            ready = true
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            setupBase()
        }

        /** 相对基准的当前缩放倍数(各向同性,取 X 分量)。 */
        private fun currentScaleRatio(): Float {
            matrix.getValues(tmpValues)
            val cur = tmpValues[Matrix.MSCALE_X]
            baseMatrix.getValues(tmpValues)
            val base = tmpValues[Matrix.MSCALE_X]
            return if (base == 0f) 1f else cur / base
        }

        private fun mappedRect(): RectF {
            val d = drawable ?: return drawRect
            drawRect.set(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
            matrix.mapRect(drawRect)
            return drawRect
        }

        /** 平移后约束:图比 view 宽/高则不留黑边,否则居中。 */
        private fun applyBounds() {
            val r = mappedRect()
            val vw = width.toFloat(); val vh = height.toFloat()
            var dx = 0f; var dy = 0f
            if (r.width() <= vw) dx = (vw - r.width()) / 2f - r.left
            else { if (r.left > 0) dx = -r.left; if (r.right < vw) dx = vw - r.right }
            if (r.height() <= vh) dy = (vh - r.height()) / 2f - r.top
            else { if (r.top > 0) dy = -r.top; if (r.bottom < vh) dy = vh - r.bottom }
            matrix.postTranslate(dx, dy)
            imageMatrix = matrix
        }

        private fun zoomTo(target: Float, px: Float, py: Float) {
            val ratio = currentScaleRatio()
            val factor = (target / ratio)
            matrix.postScale(factor, factor, px, py)
            applyBounds()
        }

        private val scaleDetector = ScaleGestureDetector(context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(d: ScaleGestureDetector): Boolean {
                    if (!ready) return true
                    var f = d.scaleFactor
                    val ratio = currentScaleRatio()
                    // 钳制总倍数在 [1, maxScale]
                    if (ratio * f < 1f) f = 1f / ratio
                    if (ratio * f > maxScale) f = maxScale / ratio
                    matrix.postScale(f, f, d.focusX, d.focusY)
                    applyBounds()
                    return true
                }
            })

        private val gestureDetector = GestureDetector(context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    onTap?.invoke(); return true
                }
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    if (!ready) return true
                    if (currentScaleRatio() > 1.05f) { matrix.set(baseMatrix); imageMatrix = matrix }
                    else zoomTo(2.5f, e.x, e.y)
                    return true
                }
                override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                    // 捏合进行中或多指时不平移:否则平移与缩放同时变换,画面会抖
                    if (scaleDetector.isInProgress || e2.pointerCount > 1) return false
                    if (currentScaleRatio() > 1.01f) {
                        matrix.postTranslate(-dx, -dy)
                        applyBounds()
                    }
                    return true
                }
            })

        override fun onTouchEvent(event: MotionEvent): Boolean {
            scaleDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
            return true
        }
    }
}
