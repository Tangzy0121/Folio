package com.folio.reader.reader

/**
 * 代码块配色(Typora 式可选渲染)。bg = 代码块底色,其余为 token 着色。
 * "auto" 跟随阅读主题深浅自动选浅/深;其余为具名配色。
 */
data class CodeStyle(
    val key: String,
    val name: String,
    val bg: Int,
    val fg: Int,        // 普通文本
    val keyword: Int,   // 关键字
    val string: Int,    // 字符串
    val comment: Int,   // 注释
    val number: Int,    // 数字
    val type: Int,      // 类型 / 大写标识符 / 内置 / 注解
    val function: Int,  // 函数名(定义/调用)
    val operator: Int   // 运算符
)

object CodeStyles {

    // 跟随阅读主题:浅色版(v19 提对比;v20 加 function/operator)
    private val AUTO_LIGHT = CodeStyle(
        "auto", "跟随",
        bg = 0xFFEEF1F4.toInt(), fg = 0xFF1F2328.toInt(),
        keyword = 0xFFCF222E.toInt(), string = 0xFF0A7C42.toInt(),
        comment = 0xFF6A737D.toInt(), number = 0xFF0550AE.toInt(), type = 0xFF1F7199.toInt(),
        function = 0xFF8250DF.toInt(), operator = 0xFF6E7781.toInt()
    )

    // 跟随阅读主题:深色版
    private val AUTO_DARK = CodeStyle(
        "auto", "跟随",
        bg = 0xFF1E2127.toInt(), fg = 0xFFD4D4D4.toInt(),
        keyword = 0xFF569CD6.toInt(), string = 0xFFCE9178.toInt(),
        comment = 0xFF6A9955.toInt(), number = 0xFFB5CEA8.toInt(), type = 0xFF4EC9B0.toInt(),
        function = 0xFFDCDCAA.toInt(), operator = 0xFFC0C4CC.toInt()
    )

    private val GITHUB = CodeStyle(
        "github", "GitHub",
        bg = 0xFFF6F8FA.toInt(), fg = 0xFF24292E.toInt(),
        keyword = 0xFFD73A49.toInt(), string = 0xFF032F62.toInt(),
        comment = 0xFF6A737D.toInt(), number = 0xFF005CC5.toInt(), type = 0xFF22863A.toInt(),
        function = 0xFF6F42C1.toInt(), operator = 0xFFD73A49.toInt()
    )

    private val DRACULA = CodeStyle(
        "dracula", "Dracula",
        bg = 0xFF282A36.toInt(), fg = 0xFFF8F8F2.toInt(),
        keyword = 0xFFFF79C6.toInt(), string = 0xFFF1FA8C.toInt(),
        comment = 0xFF6272A4.toInt(), number = 0xFFBD93F9.toInt(), type = 0xFF8BE9FD.toInt(),
        function = 0xFF50FA7B.toInt(), operator = 0xFFFF79C6.toInt()
    )

    private val NORD = CodeStyle(
        "nord", "Nord",
        bg = 0xFF2E3440.toInt(), fg = 0xFFD8DEE9.toInt(),
        keyword = 0xFF81A1C1.toInt(), string = 0xFFA3BE8C.toInt(),
        comment = 0xFF616E88.toInt(), number = 0xFFB48EAD.toInt(), type = 0xFF8FBCBB.toInt(),
        function = 0xFF88C0D0.toInt(), operator = 0xFF81A1C1.toInt()
    )

    /** 供 Aa 面板展示的可选项(用各自代表色当 swatch)。 */
    val PICKABLE: List<CodeStyle> = listOf(AUTO_LIGHT, GITHUB, DRACULA, NORD)

    /** 把存储的 key 解析为实际配色;"auto" 按阅读主题深浅选浅/深。 */
    fun resolve(key: String?, readerIsDark: Boolean): CodeStyle = when (key) {
        "github" -> GITHUB
        "dracula" -> DRACULA
        "nord" -> NORD
        else -> if (readerIsDark) AUTO_DARK else AUTO_LIGHT
    }
}
