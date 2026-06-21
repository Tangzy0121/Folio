package com.folio.reader.reader

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan

/**
 * 轻量代码高亮:单趟词法扫描,向 IDE 观感靠拢。
 * token 类:关键字 / 字符串 / 注释 / 数字 / 类型 / 函数 / 运算符 / 内置 / 注解。
 *
 * 取舍(实事求是):
 * - 纯词法,无真语义分析(分不清变量与类型的真实绑定),"函数/内置/类型"靠启发式(后跟`(`=函数、内置表、首字母大写=类型)。
 * - 单趟扫描天然避免「注释/字符串里的关键字被误着色」。
 * - 分词 [tokenize] 不依赖 android.*,可在纯 JVM 单测;[highlight] 仅按 token 上色。
 */
object CodeHighlighter {

    enum class Kind { KEYWORD, STRING, COMMENT, NUMBER, TYPE, FUNCTION, OPERATOR, BUILTIN, ANNOTATION }

    data class Token(val start: Int, val end: Int, val kind: Kind)

    private const val OP_CHARS = "+-*/%=<>!&|^~?:"

    fun highlight(code: CharSequence, language: String?, style: CodeStyle): CharSequence {
        val out = SpannableStringBuilder(code)
        for (t in tokenize(code.toString(), language)) {
            val color = when (t.kind) {
                Kind.KEYWORD -> style.keyword
                Kind.STRING -> style.string
                Kind.COMMENT -> style.comment
                Kind.NUMBER -> style.number
                Kind.TYPE -> style.type
                Kind.FUNCTION -> style.function
                Kind.OPERATOR -> style.operator
                Kind.BUILTIN -> style.type
                Kind.ANNOTATION -> style.type
            }
            out.setSpan(ForegroundColorSpan(color), t.start, t.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return out
    }

    /** 纯分词:返回各着色区段,不依赖 android,可单测。 */
    fun tokenize(s: String, language: String?): List<Token> {
        val lang = normalize(language)
        val kw = keywordsFor(lang)
        val bi = builtinsFor(lang)
        val sqlCaseInsensitive = lang == "sql"
        val hashComment = lang == "python" || lang == "ruby" || lang == "shell" || lang == "yaml" || lang == "toml"
        val dashComment = lang == "sql"
        val slashComment = !hashComment

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
                    if (s[j] == '\n' && c != '`') { break }
                    j++
                }
                tokens.add(Token(i, j.coerceAtMost(n), Kind.STRING)); i = j; continue
            }

            // 注解 / 装饰器 @name
            if (c == '@' && i + 1 < n && (s[i + 1].isLetter() || s[i + 1] == '_')) {
                var j = i + 1
                while (j < n && (s[j].isLetterOrDigit() || s[j] == '_')) j++
                tokens.add(Token(i, j, Kind.ANNOTATION)); i = j; continue
            }

            // 数字
            if (c.isDigit()) {
                var j = i + 1
                while (j < n && (s[j].isLetterOrDigit() || s[j] == '.' || s[j] == '_')) j++
                tokens.add(Token(i, j, Kind.NUMBER)); i = j; continue
            }

            // 标识符 / 关键字 / 函数 / 内置 / 类型
            if (c.isLetter() || c == '_') {
                var j = i + 1
                while (j < n && (s[j].isLetterOrDigit() || s[j] == '_')) j++
                val word = s.substring(i, j)
                val probe = if (sqlCaseInsensitive) word.uppercase() else word
                when {
                    kw.contains(probe) -> tokens.add(Token(i, j, Kind.KEYWORD))
                    nextNonSpace(s, j) == '(' -> tokens.add(Token(i, j, Kind.FUNCTION))
                    bi.contains(word) -> tokens.add(Token(i, j, Kind.BUILTIN))
                    word[0].isUpperCase() -> tokens.add(Token(i, j, Kind.TYPE))
                }
                i = j; continue
            }

            // 运算符(连续合并,如 == => -> && |=)
            if (OP_CHARS.indexOf(c) >= 0) {
                var j = i + 1
                while (j < n && OP_CHARS.indexOf(s[j]) >= 0) j++
                tokens.add(Token(i, j, Kind.OPERATOR)); i = j; continue
            }

            i++
        }
        return tokens
    }

    /** 跳过空格/制表(不跨行)后的下一个字符,用于判断标识符是否为函数调用。 */
    private fun nextNonSpace(s: String, from: Int): Char? {
        var k = from
        while (k < s.length && (s[k] == ' ' || s[k] == '\t')) k++
        return if (k < s.length) s[k] else null
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

    private fun builtinsFor(lang: String): Set<String> = when (lang) {
        "python" -> PYTHON_BI
        "js" -> JS_BI
        "kotlin" -> KOTLIN_BI
        "java" -> JAVA_BI
        "cpp" -> CPP_BI
        "go" -> GO_BI
        "rust" -> RUST_BI
        "generic" -> GENERIC_BI
        else -> emptySet()
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

    // 内置函数 / 常用类型(用 type 色)。modest 集,够日常代码片段用。
    private val PYTHON_BI = setOf(
        "print", "len", "range", "str", "int", "float", "bool", "list", "dict", "set", "tuple",
        "open", "input", "super", "isinstance", "enumerate", "zip", "map", "filter", "sum", "min",
        "max", "abs", "round", "sorted", "reversed", "type", "repr", "format", "any", "all", "next", "iter"
    )
    private val JS_BI = setOf(
        "console", "document", "window", "Math", "JSON", "Array", "Object", "String", "Number",
        "Boolean", "Promise", "Map", "Set", "Symbol", "Date", "RegExp", "parseInt", "parseFloat",
        "isNaN", "setTimeout", "setInterval", "fetch", "require", "module", "process"
    )
    private val KOTLIN_BI = setOf(
        "println", "print", "listOf", "mutableListOf", "mapOf", "mutableMapOf", "setOf", "arrayOf",
        "run", "let", "apply", "also", "with", "lazy", "require", "check", "error", "TODO",
        "String", "Int", "Boolean", "Long", "Double", "Float", "List", "Map", "Set", "Array", "MutableList"
    )
    private val JAVA_BI = setOf(
        "System", "String", "Integer", "Long", "Double", "Float", "Boolean", "Math", "Object",
        "List", "Map", "Set", "ArrayList", "HashMap", "Arrays", "Collections", "Optional", "Stream", "Exception"
    )
    private val CPP_BI = setOf(
        "std", "cout", "cin", "endl", "printf", "scanf", "malloc", "free", "vector", "string",
        "map", "set", "pair", "size_t", "NULL", "make_pair", "push_back", "sort"
    )
    private val GO_BI = setOf(
        "fmt", "len", "cap", "make", "append", "panic", "recover", "print", "println", "error",
        "string", "int", "byte", "rune", "bool", "float64", "int64", "uint", "copy"
    )
    private val RUST_BI = setOf(
        "println", "print", "vec", "format", "panic", "String", "Vec", "Option", "Result",
        "Some", "None", "Ok", "Err", "Box", "i32", "u32", "usize", "i64", "u64", "f64", "str", "bool"
    )
    private val GENERIC_BI = PYTHON_BI + JS_BI + KOTLIN_BI
}
