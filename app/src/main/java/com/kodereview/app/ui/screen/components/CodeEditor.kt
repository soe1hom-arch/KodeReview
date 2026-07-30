package com.kodereview.app.ui.screen.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.*
import androidx.compose.ui.text.input.TextFieldValue
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
    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
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
    val gutterWidth = 52.dp
    val charWidth = 8.5f  // approximate monospace char width in dp

    val highlightedText = remember(textFieldValue.text) {
        KotlinSyntaxHighlighter.highlight(textFieldValue.text)
    }

    val annotatedText = remember(textFieldValue.text, issues) {
        applyDiagnosticAnnotations(highlightedText, issues)
    }

    Row(modifier = modifier) {
        // Line number gutter
        LineNumberGutter(
            text = textFieldValue.text,
            issues = issues,
            scrollState = verticalScroll,
            lineHeight = lineHeight,
            modifier = Modifier.width(gutterWidth)
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
                onValueChange = { newValue ->
                    onValueChange(newValue)
                },
                textStyle = CODE_TEXT_STYLE,
                cursorBrush = SolidColor(CursorLine),
                visualTransformation = SyntaxHighlightTransformation(annotatedText),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .pointerInput(issues) {
                        detectTapGestures { offset ->
                            // Calculate line number from tap position
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

@Composable
private fun LineNumberGutter(
    text: String,
    issues: List<AnalysisIssue>,
    scrollState: androidx.compose.foundation.ScrollState,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier
) {
    val lines = text.lines()
    val maxLineDigits = lines.size.toString().length.coerceAtLeast(2)

    Column(
        modifier = modifier
            .background(GutterBackground)
            .verticalScroll(scrollState)
            .padding(vertical = 8.dp)
    ) {
        for ((index, _) in lines.withIndex()) {
            val lineIssues = issues.filter { it.line == index }
            val severity = lineIssues.maxOfOrNull { it.severity }
            val dotColor = when (severity) {
                Severity.ERROR -> ErrorColor
                Severity.WARNING -> WarningColor
                Severity.INFO, Severity.SUGGESTION -> InfoColor
                null -> Color.Transparent
            }

            Row(
                modifier = Modifier.height(lineHeight)
            ) {
                // Dot indicator
                Canvas(modifier = Modifier.size(12.dp).padding(top = 4.dp)) {
                    if (dotColor != Color.Transparent) {
                        drawCircle(dotColor, radius = 3.dp.toPx())
                    }
                }

                // Line number
                Text(
                    text = (index + 1).toString().padStart(maxLineDigits),
                    style = CODE_TEXT_STYLE.copy(
                        fontSize = 13.sp,
                        color = if (lineIssues.isNotEmpty()) {
                            when (severity) {
                                Severity.ERROR -> ErrorColor
                                Severity.WARNING -> WarningColor
                                else -> InfoColor
                            }
                        } else LineNumber
                    ),
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
    }
}

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

        val underlineColor = when (issue.severity) {
            Severity.ERROR -> ErrorUnderline
            Severity.WARNING -> WarningUnderline
            Severity.INFO, Severity.SUGGESTION -> InfoColor.copy(alpha = 0.3f)
        }

        builder.addStyle(
            SpanStyle(
                textDecoration = TextDecoration.Underline,
                color = underlineColor
            ),
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

private class SyntaxHighlightTransformation(
    private val annotatedString: AnnotatedString
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        return TransformedText(annotatedString, OffsetMapping.Identity)
    }
}
