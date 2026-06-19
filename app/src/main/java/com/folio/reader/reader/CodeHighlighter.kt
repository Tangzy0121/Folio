package com.folio.reader.reader

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan

/**
 * 轻量代码高亮:单趟词法扫描(逐字符判定 注释/字符串/数字/标识符),不用注解处理器、零新依赖。
 *
 * 设计取舍(实事求是):
 * - 单趟扫描天然避免「注释/字符串里的关键字被误着色」,但不做完整语法分析,转义/边界等够用即可。
 * - 语言未知或不在表中 → 回退通用关键字集 + C 系注释规则,仍能上色,不报错。
 * - 分词逻辑 [tokenize] 不依赖 android.*,可在纯 JVM 单测;[highlight] 仅按 token 上色。
 */
object CodeHighlighter {

    enum class Kind { KEYWORD, STRING, COMMENT, NUMBER, TYPE }

    data class Token(val start: Int, val end: Int, val kind: Kind)

    fun highlight(code: CharSequence, language: String?, style: CodeStyle): CharSequence {
        val out = SpannableStringBuilder(code)
        for (t in tokenize(code.toString(), language)) {
            val color = when (t.kind) {
                Kind.KEYWORD -> style.keyword
                Kind.STRING -> style.string
                Kind.COMMENT -> style.comment
                Kind.NUMBER -> style.number
                Kind.TYPE -> style.type
            }
            out.setSpan(ForegroundColorSpan(color), t.start, t.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return out
    }

    /** 纯分词:返回各着色区段,不依赖 android,可单测。 */
    fun tokenize(s: String, language: String?): List<Token> {
        val lang = normalize(language)
        val kw = keywordsFor(lang)
        val sqlCaseInsensitive = lang == "sql"
        val hashComment = lang == "python" || lang == "ruby" || lang == "shell" || lang == "yaml" || lang == "toml"
        val dashComment = lang == "sql"
        val slashComment = !hashComment  // C 系默认 //

        val tokens = ArrayList<Token>()
        val n = s.length
        var i = 0
        while (i < n) {
            val c = s[i]

            // 块注释 /* ... */
            if (c == '/' && i + 1 < n && s[i + 1] == '*') {
                val end = s.indexOf("*/", i + 2).let { if (it < 0) n else it + 2 }
                tokens.add(Token(i, end, Kind.COMMENT)); i = end; continue
            }
            // 行注释
            if (slashComment && c == '/' && i + 1 < n && s[i + 1] == '/') {
                val end = lineEnd(s, i); tokens.add(Token(i, end, Kind.COMMENT)); i = end; continue
            }
            if (hashComment && c == '#') {
                val end = lineEnd(s, i); tokens.add(Token(i, end, Kind.COMMENT)); i = end; continue
            }
            if (dashComment && c == '-' && i + 1 < n && s[i + 1] == '-') {
                val end = lineEnd(s, i); tokens.add(Token(i, end, Kind.COMMENT)); i = end; continue
            }

            // 三引号字符串(python)
            if ((c == '"' || c == '\'') && i + 2 < n && s[i + 1] == c && s[i + 2] == c) {
                val triple = "$c$c$c"
                val end = s.indexOf(triple, i + 3).let { if (it < 0) n else it + 3 }
                tokens.add(Token(i, end, Kind.STRING)); i = end; continue
            }
            // 普通字符串 " ' `
            if (c == '"' || c == '\'' || c == '`') {
                var j = i + 1
                while (j < n) {
                    if (s[j] == '\\') { j += 2; continue }
                    if (s[j] == c) { j++; break }
                    if (s[j] == '\n' && c != '`') { break }  // 反引号可跨行
                    j++
                }
                tokens.add(Token(i, j.coerceAtMost(n), Kind.STRING)); i = j; continue
            }

            // 数字
            if (c.isDigit()) {
                var j = i + 1
                while (j < n && (s[j].isLetterOrDigit() || s[j] == '.' || s[j] == '_')) j++
                tokens.add(Token(i, j, Kind.NUMBER)); i = j; continue
            }

            // 标识符 / 关键字 / 类型
            if (c.isLetter() || c == '_') {
                var j = i + 1
                while (j < n && (s[j].isLetterOrDigit() || s[j] == '_')) j++
                val word = s.substring(i, j)
                val probe = if (sqlCaseInsensitive) word.uppercase() else word
                when {
                    kw.contains(probe) -> tokens.add(Token(i, j, Kind.KEYWORD))
                    word[0].isUpperCase() -> tokens.add(Token(i, j, Kind.TYPE))
                }
                i = j; continue
            }

            i++
        }
        return tokens
    }

    private fun lineEnd(s: String, from: Int): Int =
        s.indexOf('\n', from).let { if (it < 0) s.length else it }

    private fun normalize(language: String?): String = when (language?.trim()?.lowercase()) {
        "py", "python", "python3" -> "python"
        "js", "javascript", "jsx", "node" -> "js"
        "ts", "typescript", "tsx" -> "js"
        "kt", "kotlin" -> "kotlin"
        "java" -> "java"
        "c", "cpp", "c++", "cc", "h", "hpp" -> "cpp"
        "sql", "mysql", "postgresql", "psql" -> "sql"
        "go", "golang" -> "go"
        "rs", "rust" -> "rust"
        "sh", "bash", "shell", "zsh" -> "shell"
        "yml", "yaml" -> "yaml"
        else -> "generic"
    }

    private fun keywordsFor(lang: String): Set<String> = when (lang) {
        "python" -> PYTHON
        "js" -> JS
        "kotlin" -> KOTLIN
        "java" -> JAVA
        "cpp" -> CPP
        "sql" -> SQL
        "go" -> GO
        "rust" -> RUST
        else -> GENERIC
    }

    private val PYTHON = setOf(
        "def", "class", "return", "if", "elif", "else", "for", "while", "break", "continue",
        "import", "from", "as", "with", "try", "except", "finally", "raise", "lambda", "yield",
        "global", "nonlocal", "pass", "in", "is", "not", "and", "or", "None", "True", "False",
        "async", "await", "del", "assert", "self"
    )
    private val JS = setOf(
        "const", "let", "var", "function", "return", "if", "else", "for", "while", "do", "break",
        "continue", "switch", "case", "default", "class", "extends", "new", "this", "super",
        "import", "export", "from", "as", "try", "catch", "finally", "throw", "typeof",
        "instanceof", "in", "of", "await", "async", "yield", "null", "undefined", "true", "false",
        "void", "delete", "static", "get", "set"
    )
    private val KOTLIN = setOf(
        "fun", "val", "var", "if", "else", "when", "for", "while", "do", "return", "break",
        "continue", "class", "object", "interface", "override", "private", "public", "protected",
        "internal", "import", "package", "is", "as", "in", "null", "true", "false", "this", "super",
        "companion", "data", "sealed", "enum", "try", "catch", "finally", "throw", "suspend", "by", "lateinit"
    )
    private val JAVA = setOf(
        "public", "private", "protected", "static", "final", "class", "interface", "extends",
        "implements", "return", "if", "else", "for", "while", "do", "switch", "case", "new",
        "this", "super", "void", "int", "long", "double", "float", "boolean", "char", "import",
        "package", "try", "catch", "finally", "throw", "throws", "null", "true", "false", "abstract"
    )
    private val CPP = setOf(
        "int", "long", "short", "char", "float", "double", "void", "bool", "auto", "const",
        "static", "struct", "class", "public", "private", "protected", "return", "if", "else",
        "for", "while", "do", "switch", "case", "break", "continue", "new", "delete", "using",
        "namespace", "template", "typename", "include", "define", "nullptr", "true", "false",
        "this", "sizeof", "unsigned", "signed", "enum", "union", "virtual", "override"
    )
    private val SQL = setOf(
        "SELECT", "FROM", "WHERE", "GROUP", "BY", "HAVING", "ORDER", "INSERT", "INTO", "VALUES",
        "UPDATE", "SET", "DELETE", "CREATE", "TABLE", "DROP", "ALTER", "JOIN", "LEFT", "RIGHT",
        "INNER", "OUTER", "FULL", "ON", "AS", "AND", "OR", "NOT", "NULL", "LIKE", "IN", "BETWEEN",
        "COUNT", "SUM", "AVG", "MAX", "MIN", "DISTINCT", "LIMIT", "OFFSET", "ASC", "DESC",
        "UNION", "ALL", "CASE", "WHEN", "THEN", "END"
    )
    private val GO = setOf(
        "func", "var", "const", "type", "struct", "interface", "map", "chan", "package", "import",
        "return", "if", "else", "for", "range", "switch", "case", "default", "break", "continue",
        "go", "defer", "select", "nil", "true", "false", "make", "new"
    )
    private val RUST = setOf(
        "fn", "let", "mut", "const", "struct", "enum", "impl", "trait", "pub", "use", "mod",
        "return", "if", "else", "for", "while", "loop", "match", "break", "continue", "self",
        "Self", "true", "false", "where", "async", "await", "move", "ref", "as", "dyn"
    )
    private val GENERIC = JS + KOTLIN + CPP
}
