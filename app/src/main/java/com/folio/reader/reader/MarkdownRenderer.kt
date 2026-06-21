package com.folio.reader.reader

import android.content.Context
import android.util.TypedValue
import androidx.core.graphics.ColorUtils
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.image.file.FileSchemeHandler
import io.noties.markwon.linkify.LinkifyPlugin
import io.noties.markwon.recycler.table.TableEntryPlugin

/**
 * Markwon 装配:表格 / 任务列表 / 删除线 / 自动链接 / 图片 / 内联 HTML / LaTeX 块公式,
 * 外加 Typora 化排版(标题去下划线、引用竖条、分割线、列表、行内码底色、链接色)。
 *
 * 排版强调色由当前阅读主题(theme.textColor / isDark)派生,故每次按主题重建 Markwon 即可刷新结构色。
 *
 * 注意:
 * - LaTeX 用块模式 `$$...$$`(行内 `$...$` 需 MarkwonInlineParserPlugin,易与现有插件冲突,后续再上)。
 * - `blockFitCanvas(false)`:公式按自然尺寸渲染,长公式不缩小;由外层 HorizontalScrollView 负责左右滑。
 * - 围栏代码块的高亮/底色由 CodeBlockEntry 负责,这里不管。
 */
object MarkdownRenderer {

    private const val LINK_COLOR = 0xFF4FA08B.toInt()  // 与 HTML 阅读页链接色一致的青绿

    fun create(context: Context, theme: ReaderTheme): Markwon {
        val dm = context.resources.displayMetrics
        fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, dm).toInt()
        val latexSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 18f, dm)

        val text = theme.textColor
        val quoteColor = ColorUtils.setAlphaComponent(text, 0x55)      // 引用竖条:文字色淡化
        val hrColor = ColorUtils.setAlphaComponent(text, 0x33)         // 分割线:更淡
        val inlineCodeBg = ColorUtils.setAlphaComponent(if (theme.isDark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt(), 0x14)

        return Markwon.builder(context)
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder
                        .linkColor(LINK_COLOR)
                        .blockQuoteColor(quoteColor)
                        .blockQuoteWidth(dp(3f))
                        .thematicBreakColor(hrColor)
                        .thematicBreakHeight(dp(1.5f))
                        .headingBreakHeight(0)                 // 去掉标题下默认横线,更接近 Typora
                        .bulletWidth(dp(5f))
                        .codeTextColor(text)
                        .codeBackgroundColor(inlineCodeBg)
                }
            })
            // recycler-table 专用:含表格解析 + RecyclerView 协同(必须用它,不能用普通 TablePlugin)
            .usePlugin(TableEntryPlugin.create(context))
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(ImagesPlugin.create { plugin -> plugin.addSchemeHandler(FileSchemeHandler.create()) })
            .usePlugin(HtmlPlugin.create())
            .usePlugin(JLatexMathPlugin.create(latexSize) { builder ->
                builder.theme().blockFitCanvas(false)
                // 公式解析/渲染失败就跳过,不让它拖垮整个 App
                builder.errorHandler { _, _ -> null }
            })
            .build()
    }
}
