package com.kodereview.app.analyzer.rule

import com.kodereview.app.analyzer.AnalysisIssue
import com.kodereview.app.analyzer.Severity

class RememberUsageRule : Rule {
    override val id = "RememberUsage"
    override val name = "Remember Usage"
    override val description = "Check remember {} usage patterns"

    override fun analyze(source: String, lines: List<String>): List<AnalysisIssue> {
        val issues = mutableListOf<AnalysisIssue>()
        val text = source

        // Find remember {} patterns
        val rememberPattern = Regex("""remember\s*\{""")
        val rememberWithKeys = Regex("""remember\s*\([^)]*\)\s*\{""")

        for (match in rememberPattern.findAll(text)) {
            // Check if it has keys
            val hasKeys = rememberWithKeys.containsMatchIn(
                text.substring(match.range.first, (match.range.first + 200).coerceAtMost(text.length))
            )
            if (!hasKeys) {
                val line = text.substring(0, match.range.first).count { it == '\n' }
                issues.add(
                    AnalysisIssue(
                        line = line,
                        column = match.range.first - text.lastIndexOf('\n', match.range.first) - 1,
                        length = match.value.length,
                        severity = Severity.INFO,
                        message = "remember {} without keys may cause unnecessary recomputations",
                        ruleId = id,
                        suggestion = "Add keys: remember(key1, key2) { ... }"
                    )
                )
            }
        }

        // Find derivedStateOf without remember
        val derivedPattern = Regex("""derivedStateOf\s*\{""")
        for (match in derivedPattern.findAll(text)) {
            val beforeText = text.substring(0, match.range.first)
            val line = beforeText.count { it == '\n' }
            // Check if it's wrapped in remember
            val lastRemember = beforeText.lastIndexOf("remember")
            val lastBrace = beforeText.lastIndexOf("{")
            if (lastRemember == -1 || (lastBrace > lastRemember)) {
                issues.add(
                    AnalysisIssue(
                        line = line,
                        column = match.range.first - beforeText.lastIndexOf('\n') - 1,
                        length = match.value.length,
                        severity = Severity.WARNING,
                        message = "derivedStateOf should be wrapped in remember",
                        ruleId = id,
                        suggestion = "Use: remember { derivedStateOf { ... } }"
                    )
                )
            }
        }

        return issues
    }
}
