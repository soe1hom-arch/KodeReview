package com.kodereview.app.highlighter

import androidx.compose.ui.graphics.Color
import com.kodereview.app.ui.theme.*

data class TokenStyle(
    val color: Color,
    val isBold: Boolean = false,
    val isItalic: Boolean = false
)

object HighlightingColors {
    val keyword = TokenStyle(KeywordColor, isBold = true)
    val string = TokenStyle(StringColor)
    val number = TokenStyle(NumberColor)
    val comment = TokenStyle(CommentColor, isItalic = true)
    val annotation = TokenStyle(AnnotationColor)
    val function = TokenStyle(FunctionColor)
    val variable = TokenStyle(VariableColor)
    val operator = TokenStyle(OperatorColor)
    val type = TokenStyle(TypeColor)
    val modifier = TokenStyle(ModifierColor)
    val plain = TokenStyle(VariableColor)
}
