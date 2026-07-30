package com.kodereview.app.highlighter

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * Lightweight Kotlin syntax highlighter.
 * Uses regex-based tokenization - no dependency on kotlin-compiler-embeddable.
 */
object KotlinSyntaxHighlighter {

    private val keywords = setOf(
        "fun", "val", "var", "class", "object", "interface", "data", "sealed", "enum",
        "companion", "init", "constructor", "typealias", "annotation",
        "if", "else", "when", "for", "while", "do", "return", "import", "package",
        "in", "is", "as", "try", "catch", "finally", "throw", "super", "this",
        "null", "true", "false", "break", "continue",
        "override", "open", "abstract", "private", "protected", "internal",
        "public", "final", "lateinit", "suspend", "inline", "noinline",
        "crossinline", "expect", "actual", "const", "vararg", "tailrec",
        "external", "infix", "operator", "by",
        "Int", "Long", "Float", "Double", "Boolean", "String",
        "Char", "Byte", "Short", "Any", "Unit", "Nothing",
        "List", "Set", "Map", "MutableList", "MutableSet", "MutableMap",
        "ArrayList", "HashSet", "HashMap", "Array"
    )

    private val typeIdentifiers = setOf(
        "Int", "Long", "Float", "Double", "Boolean", "String",
        "Char", "Byte", "Short", "Any", "Unit", "Nothing",
        "List", "Set", "Map", "MutableList", "MutableSet", "MutableMap"
    )

    fun highlight(source: String): AnnotatedString {
        val builder = AnnotatedString.Builder(source)
        val lines = source.lines()

        var globalOffset = 0
        for ((lineIndex, line) in lines.withIndex()) {
            highlightLine(line, builder, globalOffset)
            globalOffset += line.length + 1  // +1 for newline
        }

        return builder.toAnnotatedString()
    }

    private fun highlightLine(line: String, builder: AnnotatedString.Builder, offset: Int) {
        var i = 0
        val len = line.length

        while (i < len) {
            val ch = line[i]

            when {
                // Single-line comment
                ch == '/' && i + 1 < len && line[i + 1] == '/' -> {
                    builder.addStyle(
                        SpanStyle(color = HighlightingColors.comment.color, fontStyle = FontStyle.Italic),
                        offset + i, offset + len
                    )
                    return
                }

                // Block comment (simplified - single line only)
                ch == '/' && i + 1 < len && line[i + 1] == '*' -> {
                    val end = line.indexOf("*/", i + 2)
                    val commentEnd = if (end != -1) end + 2 else len
                    builder.addStyle(
                        SpanStyle(color = HighlightingColors.comment.color, fontStyle = FontStyle.Italic),
                        offset + i, offset + commentEnd
                    )
                    i = commentEnd
                }

                // String literal
                ch == '"' -> {
                    val strEnd = findStringEnd(line, i)
                    builder.addStyle(
                        SpanStyle(color = HighlightingColors.string.color),
                        offset + i, offset + strEnd
                    )
                    i = strEnd
                }

                // Character literal
                ch == '\'' -> {
                    val charEnd = if (i + 2 < len && line[i + 1] == '\\') i + 4 else i + 2
                    if (charEnd <= len) {
                        builder.addStyle(
                            SpanStyle(color = HighlightingColors.string.color),
                            offset + i, offset + charEnd
                        )
                        i = charEnd
                    } else i++
                }

                // Number literals
                ch.isDigit() -> {
                    val numEnd = findNumberEnd(line, i)
                    builder.addStyle(
                        SpanStyle(color = HighlightingColors.number.color),
                        offset + i, offset + numEnd
                    )
                    i = numEnd
                }

                // @Annotation
                ch == '@' -> {
                    val annoEnd = findIdentifierEnd(line, i + 1)
                    builder.addStyle(
                        SpanStyle(color = HighlightingColors.annotation.color),
                        offset + i, offset + if (annoEnd > i + 1) annoEnd else i + 1
                    )
                    i = if (annoEnd > i) annoEnd else i + 1
                }

                // Identifier or keyword
                ch == '_' || ch.isLetter() -> {
                    val identEnd = findIdentifierEnd(line, i)
                    val word = line.substring(i, identEnd)

                    if (word in keywords) {
                        val style = if (word in typeIdentifiers) HighlightingColors.type
                        else if (word in setOf("override", "open", "abstract", "private", "protected", "internal",
                                "public", "final", "lateinit", "suspend", "inline", "noinline", "crossinline",
                                "expect", "actual", "const", "vararg", "tailrec", "external", "infix", "operator",
                                "data", "sealed", "enum", "annotation", "companion", "inner", "value"))
                            HighlightingColors.modifier
                        else HighlightingColors.keyword

                        builder.addStyle(
                            SpanStyle(
                                color = style.color,
                                fontWeight = if (style.isBold) FontWeight.Bold else null
                            ),
                            offset + i, offset + identEnd
                        )
                    }
                    i = identEnd
                }

                // Operators
                ch in "+-*/%!|&<>=~^?:;" -> {
                    builder.addStyle(
                        SpanStyle(color = HighlightingColors.operator.color),
                        offset + i, offset + i + 1
                    )
                    i++
                }

                else -> i++
            }
        }
    }

    private fun findStringEnd(line: String, start: Int): Int {
        var i = start + 1
        while (i < line.length) {
            when (line[i]) {
                '\\' -> i += 2  // skip escape
                '"' -> return i + 1
                else -> i++
            }
        }
        return line.length
    }

    private fun findIdentifierEnd(line: String, start: Int): Int {
        var i = start
        while (i < line.length && (line[i].isLetterOrDigit() || line[i] == '_')) i++
        return i
    }

    private fun findNumberEnd(line: String, start: Int): Int {
        var i = start
        while (i < line.length && (line[i].isDigit() || line[i] == '.' || line[i] == 'f' || line[i] == 'L')) i++
        return i
    }
}
