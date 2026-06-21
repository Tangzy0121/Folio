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
 * 标签管理(Material 底部抽屉):列全部标签,点行=多选筛选(打勾),右侧改名/删除(批量作用所有文件)。
 * 溢出场景的「管理/全部」入口即开此抽屉。
 */
class TagManagerSheet(
    private val ctx: Context,
    private val allTags: () -> List<String>,
    private val selected: () -> Set<String>,
    private val onToggleFilter: (String) -> Unit,
    private val onRename: (String, String) -> Unit,
    private val onDelete: (String) -> Unit,
    private val onChanged: () -> Unit
) {
    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
    private fun attr(a: Int, def: Int) = MaterialColors.getColor(ctx, a, def)
    private val onSurface by lazy { attr(com.google.android.material.R.attr.colorOnSurface, 0xFF23272E.toInt()) }
    private val onSurfaceVar by lazy { attr(com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF6B7178.toInt()) }
    private val error by lazy { attr(com.google.android.material.R.attr.colorError, 0xFFB3261E.toInt()) }
    private val outline by lazy { attr(com.google.android.material.R.attr.colorOutline, 0x22888888) }
    private val accent = 0xFF4FA08B.toInt()

    private lateinit var listBox: LinearLayout

    fun show() {
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, dp(12))
        }
        root.addView(TextView(ctx).apply {
            text = "标签 · 筛选与管理"
            setTextColor(onSurface)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(20), dp(8), dp(20), dp(2))
        })
        root.addView(TextView(ctx).apply {
            text = "点标签名筛选(可多选);右侧可改名 / 删除"
            setTextColor(onSurfaceVar)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(20), 0, dp(20), dp(10))
        })
        root.addView(divider())

        listBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(ctx).apply {
            addView(listBox)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(380))
        })
        populate()

        val dialog = AdaptiveSheet.create(ctx, root)
        dialog.setOnDismissListener { onChanged() }
        dialog.show()
    }

    private fun populate() {
        listBox.removeAllViews()
        val sel = selected()
        val all = allTags()
        if (all.isEmpty()) {
            listBox.addView(TextView(ctx).apply {
                text = "还没有标签。长按文件 →「标签」可添加。"
                setTextColor(onSurfaceVar)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(dp(20), dp(16), dp(20), dp(16))
            })
            return
        }
        all.forEach { tag -> listBox.addView(row(tag, sel.contains(tag))) }
    }

    private fun row(tag: String, filtered: Boolean): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(11), dp(12), dp(11))
        }
        // 左:筛选勾
        row.addView(ImageView(ctx).apply {
            setImageResource(R.drawable.ic_check)
            setColorFilter(if (filtered) accent else outline)
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
        })
        // 中:标签名(点=切换筛选)
        row.addView(TextView(ctx).apply {
            text = "#$tag"
            setTextColor(if (filtered) accent else onSurface)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(dp(12), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            isClickable = true
            background = ripple()
            setOnClickListener { onToggleFilter(tag); populate() }
        })
        // 右:改名
        row.addView(iconBtn(R.drawable.ic_edit, onSurfaceVar) { promptRename(tag) })
        // 右:删除
        row.addView(iconBtn(R.drawable.ic_delete, error) { confirmDelete(tag) })
        return row
    }

    private fun iconBtn(res: Int, color: Int, onClick: () -> Unit) = ImageView(ctx).apply {
        setImageResource(res)
        setColorFilter(color)
        setPadding(dp(8), dp(8), dp(8), dp(8))
        layoutParams = LinearLayout.LayoutParams(dp(38), dp(38))
        isClickable = true
        background = ripple()
        setOnClickListener { onClick() }
    }

    private fun promptRename(old: String) {
        val view = View.inflate(ctx, R.layout.dialog_rename, null)
        view.findViewById<TextInputLayout>(R.id.renameLayout)?.hint = "标签名"
        val input = view.findViewById<TextInputEditText>(R.id.renameInput)
        input.setText(old)
        input.setSelection(old.length)
        MaterialAlertDialogBuilder(ctx)
            .setTitle("重命名标签")
            .setView(view)
            .setPositiveButton("确定") { _, _ ->
                val n = input.text.toString().trim()
                if (n.isNotEmpty() && n != old) { onRename(old, n); populate() }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun confirmDelete(tag: String) {
        MaterialAlertDialogBuilder(ctx)
            .setTitle("删除标签「$tag」?")
            .setMessage("会从所有文件移除该标签(文件本身不受影响)。")
            .setPositiveButton("删除") { _, _ -> onDelete(tag); populate() }
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
