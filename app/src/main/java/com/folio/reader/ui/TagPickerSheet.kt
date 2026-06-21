package com.folio.reader.ui

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.folio.reader.R

/**
 * 单文件标签编辑(Material 底部抽屉):勾选已有标签即时增删,可新建标签;停留不关、改完一次性刷新。
 */
class TagPickerSheet(
    private val ctx: Context,
    private val fileName: String,
    private val allTags: () -> List<String>,
    private val fileTags: () -> List<String>,
    private val onToggle: (String) -> Unit,
    private val onCreate: (String) -> Unit,
    private val onDone: () -> Unit
) {
    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
    private fun attr(a: Int, def: Int) = MaterialColors.getColor(ctx, a, def)
    private val onSurface by lazy { attr(com.google.android.material.R.attr.colorOnSurface, 0xFF23272E.toInt()) }
    private val onSurfaceVar by lazy { attr(com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF6B7178.toInt()) }
    private val outline by lazy { attr(com.google.android.material.R.attr.colorOutline, 0x22888888) }
    private val accent = 0xFF4FA08B.toInt()

    private lateinit var listBox: LinearLayout

    fun show() {
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, dp(12))
        }
        root.addView(TextView(ctx).apply {
            text = "为「$fileName」设置标签"
            setTextColor(onSurface)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            setPadding(dp(20), dp(8), dp(20), dp(10))
        })
        root.addView(divider())

        listBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(ctx).apply {
            addView(listBox)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(360))
        })
        populate()

        val dialog = AdaptiveSheet.create(ctx, root)
        dialog.setOnDismissListener { onDone() }
        dialog.show()
    }

    private fun populate() {
        listBox.removeAllViews()
        val mine = fileTags().toSet()
        val all = allTags()
        if (all.isEmpty()) {
            listBox.addView(TextView(ctx).apply {
                text = "还没有标签,点下面新建一个"
                setTextColor(onSurfaceVar)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(dp(20), dp(14), dp(20), dp(14))
            })
        }
        all.forEach { tag ->
            listBox.addView(tagRow(tag, mine.contains(tag)))
        }
        listBox.addView(newTagRow())
    }

    private fun tagRow(tag: String, checked: Boolean): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(13), dp(20), dp(13))
            isClickable = true
            background = ripple()
            setOnClickListener { onToggle(tag); populate() }
        }
        row.addView(ImageView(ctx).apply {
            setImageResource(R.drawable.ic_folder)
            setColorFilter(if (checked) accent else onSurfaceVar)
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
        })
        row.addView(TextView(ctx).apply {
            text = tag
            setTextColor(onSurface)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(dp(14), 0, 0, 0)
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

    private fun newTagRow(): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(13), dp(20), dp(13))
            isClickable = true
            background = ripple()
            setOnClickListener { promptNewTag() }
        }
        row.addView(ImageView(ctx).apply {
            setImageResource(R.drawable.ic_edit)
            setColorFilter(accent)
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
        })
        row.addView(TextView(ctx).apply {
            text = "新建标签…"
            setTextColor(accent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(dp(14), 0, 0, 0)
        })
        return row
    }

    private fun promptNewTag() {
        val view = View.inflate(ctx, R.layout.dialog_rename, null)
        view.findViewById<TextInputLayout>(R.id.renameLayout)?.hint = "标签名"
        val input = view.findViewById<TextInputEditText>(R.id.renameInput)
        MaterialAlertDialogBuilder(ctx)
            .setTitle("新建标签")
            .setView(view)
            .setPositiveButton("确定") { _, _ ->
                val t = input.text.toString().trim()
                if (t.isNotEmpty()) { onCreate(t); populate() }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun divider() = View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
        setBackgroundColor(outline)
    }

    private fun ripple(): android.graphics.drawable.Drawable? {
        val tv = TypedValue()
        ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
        return if (tv.resourceId != 0) androidx.core.content.ContextCompat.getDrawable(ctx, tv.resourceId) else null
    }
}
