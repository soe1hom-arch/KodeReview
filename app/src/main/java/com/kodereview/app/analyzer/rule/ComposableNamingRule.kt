package com.kodereview.app.analyzer.rule

import com.kodereview.app.analyzer.AnalysisIssue
import com.kodereview.app.analyzer.Severity

/**
 * Checks that @Composable functions use PascalCase naming convention.
 */
class ComposableNamingRule : Rule {
    override val id = "ComposableNaming"
    override val name = "Composable Naming"
    override val description = "Functions annotated with @Composable should use PascalCase"

    override fun analyze(source: String, lines: List<String>): List<AnalysisIssue> {
        val issues = mutableListOf<AnalysisIssue>()
        val sourceLines = source.lines()

        for ((lineIndex, line) in sourceLines.withIndex()) {
            val trimmed = line.trim()
            // Check for @Composable annotation on previous lines
            if (trimmed.startsWith("@Composable")) {
                // Look at next line for function
                if (lineIndex + 1 < sourceLines.size) {
                    val nextLine = sourceLines[lineIndex + 1].trim()
                    checkFunctionName(nextLine, lineIndex + 1, issues)
                }
            } else if (trimmed.contains("@Composable")) {
                // Inline annotation like: @Composable fun myComposable()
                checkFunctionName(trimmed, lineIndex, issues)
            }
        }

        return issues
    }

    private fun checkFunctionName(line: String, lineIndex: Int, issues: MutableList<AnalysisIssue>) {
        // Match: fun functionName(
        val funMatch = Regex("""fun\s+([a-zA-Z_]\w*)\s*[\(\<]""").find(line)
        if (funMatch != null) {
            val name = funMatch.groupValues[1]
            if (name[0].isLowerCase()) {
                val col = line.indexOf(name)
                issues.add(
                    AnalysisIssue(
                        line = lineIndex,
                        column = col,
                        length = name.length,
                        severity = Severity.WARNING,
                        message = "Composable function '$name' should use PascalCase (e.g., '${name.replaceFirstChar { it.uppercase() }}')",
                        ruleId = id,
                        suggestion = "Rename to '${name.replaceFirstChar { it.uppercase() }}'"
                    )
                )
            }
        }
    }
}
