package com.kodereview.app.analyzer.rule

import com.kodereview.app.analyzer.AnalysisIssue
import com.kodereview.app.analyzer.Severity

class UnusedImportRule : Rule {
    override val id = "UnusedImport"
    override val name = "Unused Import"
    override val description = "Detect potentially unused imports (basic check)"

    override fun analyze(source: String, lines: List<String>): List<AnalysisIssue> {
        val issues = mutableListOf<AnalysisIssue>()
        val importLines = mutableListOf<Pair<Int, String>>()  // line index, import text
        val usedNames = mutableSetOf<String>()

        // First pass: collect imports and used identifiers
        for ((lineIndex, line) in lines.withIndex()) {
            val trimmed = line.trim()
            val importMatch = Regex("""^import\s+([\w.]+(?:\*)?)\s*$""").find(trimmed)
            if (importMatch != null) {
                importLines.add(lineIndex to importMatch.groupValues[1])
                continue
            }

            // Skip package, comments, annotations
            if (trimmed.startsWith("package ") || trimmed.startsWith("//") || 
                trimmed.startsWith("*") || trimmed.startsWith("/*")) continue

            // Extract all identifiers from the line
            val identifiers = Regex("""\b([A-Z]\w*)\b""").findAll(trimmed)
            for (id in identifiers) {
                usedNames.add(id.value)
            }
        }

        // Second pass: check which imports are used
        for ((lineIndex, importPath) in importLines) {
            val simpleName = importPath.split(".").last().removeSuffix("*")
            if (simpleName == "*") {
                val packageName = importPath.removeSuffix(".*")
                // Wildcard imports are harder to check - skip for now
                continue
            }
            if (simpleName !in usedNames && !importPath.startsWith("android") && 
                !importPath.startsWith("kotlin") && !importPath.startsWith("java") &&
                !importPath.startsWith("org.jetbrains")
            ) {
                issues.add(
                    AnalysisIssue(
                        line = lineIndex,
                        column = 0,
                        length = lines[lineIndex].trimStart().length,
                        severity = Severity.INFO,
                        message = "Import '$importPath' may be unused",
                        ruleId = id,
                        suggestion = "Remove unused import"
                    )
                )
            }
        }

        return issues
    }
}
