package com.kodereview.app.analyzer.rule

import com.kodereview.app.analyzer.AnalysisIssue
import com.kodereview.app.analyzer.Severity

class ExpressionBodyRule : Rule {
    override val id = "ExpressionBody"
    override val name = "Expression Body"
    override val description = "Suggest expression body syntax for single-expression functions"

    override fun analyze(source: String, lines: List<String>): List<AnalysisIssue> {
        val issues = mutableListOf<AnalysisIssue>()
        val text = source.lines()

        var i = 0
        while (i < text.size) {
            val line = text[i]
            val trimmed = line.trim()

            // Look for function definition
            if (trimmed.contains("fun ") && trimmed.endsWith("{")) {
                val funcLine = trimmed
                // Check if next lines contain only one expression before the closing brace
                val bodyLines = mutableListOf<String>()
                var j = i + 1
                var braceCount = 1
                
                while (j < text.size && braceCount > 0) {
                    val bodyLine = text[j]
                    for (c in bodyLine) {
                        if (c == '{') braceCount++
                        if (c == '}') braceCount--
                    }
                    if (braceCount > 0) {
                        bodyLines.add(bodyLine.trim())
                    }
                    j++
                }

                // If only one expression line (plus possible blank lines)
                val nonBlankLines = bodyLines.filter { it.isNotBlank() && !it.startsWith("//") }
                if (nonBlankLines.size == 1 && !nonBlankLines[0].contains("if") && 
                    !nonBlankLines[0].contains("when") && !nonBlankLines[0].contains("for") &&
                    !nonBlankLines[0].contains("while") && !nonBlankLines[0].contains("try") &&
                    !nonBlankLines[0].contains("return")
                ) {
                    val exprLine = nonBlankLines[0].removeSuffix(";")
                    val funcDefWithoutBrace = trimmed.removeSuffix("{").trim()
                    issues.add(
                        AnalysisIssue(
                            line = i,
                            column = line.indexOf("fun"),
                            length = line.trimStart().length,
                            severity = Severity.SUGGESTION,
                            message = "Function with single expression can use expression body syntax",
                            ruleId = id,
                            suggestion = "$funcDefWithoutBrace = $exprLine"
                        )
                    )
                }
            }
            i++
        }

        return issues
    }
}
