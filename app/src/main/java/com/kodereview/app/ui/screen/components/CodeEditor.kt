package com.kodereview.app.ui.screen.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kodereview.app.analyzer.AnalysisIssue
import com.kodereview.app.analyzer.Severity
import com.kodereview.app.highlighter.KotlinSyntaxHighlighter
import com.kodereview.app.ui.theme.*

private val CODE_TEXT_STYLE = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 14.sp,
    lineHeight = 22.sp,
    color = OnSurface
)

@Composable
fun CodeEditor(
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    issues: List<AnalysisIssue>,
    onIssueClick: (AnalysisIssue) -> Unit,
    modifier: Modifier = Modifier
) {
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()
    val lineHeight = 22.sp

    val highlightedText = remember(textFieldValue.text) {
        KotlinSyntaxHighlighter.highlight(textFieldValue.text)
    }

    val annotatedText = remember(textFieldValue.text, issues) {
        applyDiagnosticAnnotations(highlightedText, issues)
    }

    Row(modifier = modifier) {
        // Line number gutter with issue indicators
        Column(
            modifier = Modifier
                .width(48.dp)
                .background(GutterBackground)
                .verticalScroll(verticalScroll)
                .padding(vertical = 8.dp)
        ) {
            val lines = textFieldValue.text.lines()
            for ((index, _) in lines.withIndex()) {
                val lineIssues = issues.filter { it.line == index }
                val maxSeverity = lineIssues.maxOfOrNull { it.severity }
                val dotColor = when (maxSeverity) {
                    Severity.ERROR -> ErrorColor
                    Severity.WARNING -> WarningColor
                    Severity.INFO, Severity.SUGGESTION -> InfoColor
                    null -> Color.Transparent
                }

                Row(
                    modifier = Modifier
                        .height(lineHeight)
                        .then(
                            if (dotColor != Color.Transparent) {
                                Modifier.background(dotColor.copy(alpha = 0.08f))
                            } else Modifier
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Issue dot indicator
                    if (dotColor != Color.Transparent) {
                        Canvas(modifier = Modifier.size(14.dp).padding(start = 2.dp)) {
                            drawCircle(dotColor, radius = 6.dp.toPx())
                        }
                    } else {
                        Spacer(Modifier.width(14.dp))
                    }
                    // Line number
                    Text(
                        text = (index + 1).toString().padStart(2),
                        style = CODE_TEXT_STYLE.copy(fontSize = 11.sp),
                        color = if (lineIssues.isNotEmpty()) dotColor else LineNumber,
                        fontWeight = if (lineIssues.isNotEmpty()) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // Left colored border for lines with issues
        val maxSeverityAll = issues.maxOfOrNull { it.severity }
        val borderColor = when (maxSeverityAll) {
            Severity.ERROR -> ErrorColor
            Severity.WARNING -> WarningColor
            Severity.INFO, Severity.SUGGESTION -> InfoColor
            null -> Color.Transparent
        }

        Box(
            modifier = Modifier
                .width(if (borderColor != Color.Transparent) 3.dp else 0.dp)
                .fillMaxHeight()
                .background(borderColor)
        )

        // Editor area
        Box(
            modifier = Modifier
                .weight(1f)
                .background(EditorBackground)
                .verticalScroll(verticalScroll)
                .horizontalScroll(horizontalScroll)
        ) {
            BasicTextField(
                value = textFieldValue,
                onValueChange = { onValueChange(it) },
                textStyle = CODE_TEXT_STYLE,
                cursorBrush = SolidColor(CursorLine),
                visualTransformation = object : VisualTransformation {
                    override fun filter(text: AnnotatedString): TransformedText {
                        return TransformedText(annotatedText, OffsetMapping.Identity)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .pointerInput(issues) {
                        detectTapGestures { offset ->
                            val line = (offset.y / lineHeight.toPx()).toInt()
                            val matchingIssue = issues.find { it.line == line }
                            if (matchingIssue != null) {
                                onIssueClick(matchingIssue)
                            }
                        }
                    }
            )
        }
    }
}

/**
 * Apply diagnostic annotations to highlighted code.
 * Uses SOLID colors for maximum visibility.
 */
private fun applyDiagnosticAnnotations(
    highlighted: AnnotatedString,
    issues: List<AnalysisIssue>
): AnnotatedString {
    val builder = AnnotatedString.Builder(highlighted)
    val source = highlighted.text

    for (issue in issues) {
        val lineStart = getLineStart(source, issue.line)
        val start = lineStart + issue.column
        val end = (start + issue.length).coerceAtMost(source.length)

        if (start >= source.length || start < 0) continue

        val (underlineColor, bgColor) = when (issue.severity) {
            Severity.ERROR -> ErrorColor.copy(alpha = 0.9f) to ErrorColor.copy(alpha = 0.18f)
            Severity.WARNING -> WarningColor.copy(alpha = 0.8f) to WarningColor.copy(alpha = 0.14f)
            Severity.INFO, Severity.SUGGESTION -> InfoColor.copy(alpha = 0.6f) to InfoColor.copy(alpha = 0.10f)
        }

        // Thick wavy underline
        builder.addStyle(
            SpanStyle(
                textDecoration = TextDecoration.Underline,
                color = underlineColor
            ),
            start,
            end
        )

        // Background highlight
        builder.addStyle(
            SpanStyle(background = bgColor),
            start,
            end
        )
    }

    return builder.toAnnotatedString()
}

private fun getLineStart(source: String, line: Int): Int {
    var currentLine = 0
    for (i in source.indices) {
        if (currentLine == line) return i
        if (source[i] == '\n') currentLine++
    }
    return source.length
}
