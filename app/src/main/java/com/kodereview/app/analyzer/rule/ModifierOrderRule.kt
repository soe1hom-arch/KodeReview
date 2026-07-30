package com.kodereview.app.analyzer.rule

import com.kodereview.app.analyzer.AnalysisIssue
import com.kodereview.app.analyzer.Severity

/**
 * Checks Modifier chain ordering best practices.
 * Common convention: size-related modifiers before padding-related ones.
 */
class ModifierOrderRule : Rule {
    override val id = "ModifierOrder"
    override val name = "Modifier Order"
    override val description = "Check modifier ordering conventions in Compose"

    // Modifiers that typically should come before padding
    private val sizeModifiers = listOf(
        ".size(", ".width(", ".height(", ".fillMaxWidth(", ".fillMaxHeight(",
        ".fillMaxSize(", ".aspectRatio(", ".requiredSize(", ".requiredWidth(", ".requiredHeight(",
        ".defaultMinSize(", ".widthIn(", ".heightIn(", ".sizeIn("
    )

    // Modifiers that should come after size modifiers
    private val paddingModifiers = listOf(
        ".padding(", ".paddingFrom(", ".paddingValues(", ".paddingTop(", ".paddingBottom(",
        ".paddingStart(", ".paddingEnd(", ".paddingHorizontal(", ".paddingVertical("
    )

    override fun analyze(source: String, lines: List<String>): List<AnalysisIssue> {
        val issues = mutableListOf<AnalysisIssue>()
        val sourceLines = source.lines()

        for ((lineIndex, line) in sourceLines.withIndex()) {
            // Look for modifier chains spanning multiple lines
            if (line.contains(".padding(") || line.contains(".size(") ||
                line.contains(".width(") || line.contains(".height(") ||
                line.contains(".fillMaxWidth(") || line.contains(".fillMaxHeight(")
            ) {
                checkModifierChain(line, lineIndex, issues)
            }

            // Check for multi-line modifier chains
            if (line.trimEnd().endsWith(".") && lineIndex + 1 < sourceLines.size) {
                checkMultilineChain(line, sourceLines[lineIndex + 1], lineIndex, issues)
            }
        }

        return issues
    }

    private fun checkModifierChain(line: String, lineIndex: Int, issues: MutableList<AnalysisIssue>) {
        val modifierStart = line.indexOf("Modifier")
        if (modifierStart == -1) return

        val chain = line.substring(modifierStart)
        var lastPaddingIndex = -1
        var lastSizeIndex = -1

        for ((idx, modifier) in paddingModifiers.withIndex()) {
            val pos = chain.indexOf(modifier)
            if (pos != -1 && (lastPaddingIndex == -1 || pos < lastPaddingIndex)) {
                lastPaddingIndex = pos
            }
        }

        for ((idx, modifier) in sizeModifiers.withIndex()) {
            val pos = chain.indexOf(modifier)
            if (pos != -1 && (lastSizeIndex == -1 || pos < lastSizeIndex)) {
                lastSizeIndex = pos
            }
        }

        // If padding comes before size, that's a warning
        if (lastPaddingIndex != -1 && lastSizeIndex != -1 && lastPaddingIndex < lastSizeIndex) {
            val col = line.indexOf("Modifier")
            issues.add(
                AnalysisIssue(
                    line = lineIndex,
                    column = col,
                    length = line.trimStart().length,
                    severity = Severity.WARNING,
                    message = "Size-related modifiers should come before padding modifiers in the chain",
                    ruleId = id,
                    suggestion = "Move .size()/.width()/.height() before .padding()"
                )
            )
        }
    }

    private fun checkMultilineChain(
        currentLine: String,
        nextLine: String,
        lineIndex: Int,
        issues: MutableList<AnalysisIssue>
    ) {
        val trimmed = currentLine.trim()
        if (trimmed.endsWith(".") && trimmed.contains("Modifier")) {
            val nextTrimmed = nextLine.trim()
            val hasPadding = nextTrimmed.startsWith("padding(")
            val hasSize = nextTrimmed.startsWith("size(") || nextTrimmed.startsWith("width(") ||
                    nextTrimmed.startsWith("height(") || nextTrimmed.startsWith("fillMax")

            if (hasPadding && currentLine.let { it.contains(".size(") || it.contains(".width(") ||
                        it.contains(".height(") || it.contains(".fillMax") }) {
                issues.add(
                    AnalysisIssue(
                        line = lineIndex,
                        column = 0,
                        length = currentLine.trimStart().length,
                        severity = Severity.INFO,
                        message = "Modifier chain: size modifier should be on the same line as padding or before it",
                        ruleId = id
                    )
                )
            }
        }
    }
}
