package com.kodereview.app.highlighter

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.kotlin.lexer.KotlinLexer
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtKeywordToken
import org.jetbrains.kotlin.lexer.KtSingleValueToken
import org.jetbrains.kotlin.com.intellij.psi.tree.IElementType

object KotlinSyntaxHighlighter {

    // Set of type-like identifiers for coloring
    private val typeKeywords = setOf(
        "Int", "Long", "Float", "Double", "Boolean", "String",
        "Char", "Byte", "Short", "Any", "Unit", "Nothing",
        "List", "Set", "Map", "MutableList", "MutableSet", "MutableMap",
        "ArrayList", "HashSet", "HashMap", "Array",
        "IntArray", "LongArray", "FloatArray", "DoubleArray", "BooleanArray",
        "CharArray", "ShortArray", "ByteArray"
    )

    fun highlight(source: String): AnnotatedString {
        val builder = AnnotatedString.Builder(source)
        val lexer = KotlinLexer()

        try {
            // Initialize lexer with source, start, end, initial state
            lexer.start(source, 0, source.length, 0)

            while (lexer.tokenType != null) {
                val tokenStart = lexer.tokenStart
                val tokenEnd = lexer.tokenEnd
                val tokenText = source.substring(tokenStart, tokenEnd)
                val tokenType = lexer.tokenType ?: break

                val style = getTokenStyle(tokenType, tokenText)
                if (style != null) {
                    builder.addStyle(
                        SpanStyle(
                            color = style.color,
                            fontWeight = if (style.isBold) FontWeight.Bold else null,
                            fontStyle = if (style.isItalic) FontStyle.Italic else null
                        ),
                        tokenStart,
                        tokenEnd
                    )
                }

                lexer.advance()
            }
        } catch (e: Exception) {
            // If lexer fails, return plain text
        }

        return builder.toAnnotatedString()
    }

    fun getTokenStyle(tokenType: IElementType, tokenText: String): TokenStyle? {
        return when (tokenType) {
            // ── Comments ──
            KtTokens.BLOCK_COMMENT, KtTokens.EOL_COMMENT, KtTokens.SHEBANG_COMMENT,
            KtTokens.DOC_COMMENT -> HighlightingColors.comment

            // ── String literals ──
            KtTokens.OPEN_QUOTE, KtTokens.CLOSING_QUOTE,
            KtTokens.REGULAR_STRING_PART, KtTokens.ESCAPE_SEQUENCE,
            KtTokens.SHORT_TEMPLATE_ENTRY_START, KtTokens.LONG_TEMPLATE_ENTRY_START,
            KtTokens.LONG_TEMPLATE_ENTRY_END, KtTokens.CHARACTER_LITERAL ->
                HighlightingColors.string

            // ── Number literals ──
            KtTokens.INTEGER_LITERAL, KtTokens.FLOAT_LITERAL ->
                HighlightingColors.number

            // ── Annotations ──
            KtTokens.AT -> HighlightingColors.annotation

            // ── Keywords and modifiers ──
            is KtModifierKeywordToken -> getModifierKeywordStyle(tokenText)
            is KtKeywordToken -> getKeywordStyle(tokenText)
            is KtSingleValueToken -> getSingleValueStyle(tokenText)

            // ── Identifiers ──
            KtTokens.IDENTIFIER -> {
                if (tokenText in typeKeywords) HighlightingColors.type
                else null  // Don't color plain identifiers
            }

            else -> null
        }
    }

    private fun getModifierKeywordStyle(text: String): TokenStyle {
        return HighlightingColors.modifier
    }

    private fun getKeywordStyle(text: String): TokenStyle {
        return HighlightingColors.keyword
    }

    private fun getSingleValueStyle(text: String): TokenStyle? {
        // Operators get operator color
        return when (text) {
            "+", "-", "*", "/", "%", "++", "--", "..", "..<",
            "!", "||", "&&", "!=", "==", "===", "!==",
            ">", "<", ">=", "<=",
            "+=", "-=", "*=", "/=", "%=",
            "?", "::", "->", "=>",
            "?.", "!!", ":", ";", ",", "@", "#", "~",
            "&", "|" -> HighlightingColors.operator
            "(", ")", "[", "]", "{", "}" -> null  // Brackets - no color
            ".", "=" -> null  // Dot and assignment - no color
            else -> null
        }
    }
}
