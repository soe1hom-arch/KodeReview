package com.kodereview.app.analyzer.rule

import com.kodereview.app.analyzer.AnalysisIssue
import com.kodereview.app.analyzer.Severity

class NamingConventionRule : Rule {
    override val id = "NamingConvention"
    override val name = "Naming Convention"
    override val description = "Check Kotlin naming conventions"

    override fun analyze(source: String, lines: List<String>): List<AnalysisIssue> {
        val issues = mutableListOf<AnalysisIssue>()

        for ((lineIndex, line) in lines.withIndex()) {
            val trimmed = line.trim()

            // Skip comments and strings
            if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) continue

            // Check class/object names (PascalCase)
            val classMatch = Regex("""(?:class|object|interface|enum class|sealed class|data class)\s+(\w+)""").find(trimmed)
            if (classMatch != null) {
                val name = classMatch.groupValues[1]
                if (name[0].isLowerCase()) {
                    issues.add(
                        AnalysisIssue(
                            line = lineIndex,
                            column = trimmed.indexOf(name),
                            length = name.length,
                            severity = Severity.WARNING,
                            message = "Class/object name '$name' should start with uppercase (PascalCase)",
                            ruleId = id,
                            suggestion = "Rename to '${name.replaceFirstChar { it.uppercase() }}'"
                        )
                    )
                }
            }

            // Check function names (camelCase) - skip constructors and operators
            val funMatch = Regex("""fun\s+(\w+)""").find(trimmed)
            if (funMatch != null) {
                val name = funMatch.groupValues[1]
                if (name[0].isUpperCase() && !trimmed.contains("@Composable")) {
                    // Only warn for non-Composable functions that use PascalCase
                    issues.add(
                        AnalysisIssue(
                            line = lineIndex,
                            column = trimmed.indexOf(name),
                            length = name.length,
                            severity = Severity.INFO,
                            message = "Regular function '$name' should start with lowercase (camelCase)",
                            ruleId = id,
                            suggestion = "Rename to '${name.replaceFirstChar { it.lowercase() }}'"
                        )
                    )
                }
            }

            // Check constant names (UPPER_SNAKE_CASE for const val)
            if (trimmed.startsWith("const val ")) {
                val constMatch = Regex("""const val\s+(\w+)""").find(trimmed)
                if (constMatch != null) {
                    val name = constMatch.groupValues[1]
                    if (name != name.uppercase() && !name.contains("_")) {
                        issues.add(
                            AnalysisIssue(
                                line = lineIndex,
                                column = trimmed.indexOf(name),
                                length = name.length,
                                severity = Severity.INFO,
                                message = "Const value '$name' should use UPPER_SNAKE_CASE",
                                ruleId = id,
                                suggestion = "Rename to '${name.replace(Regex("(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])", "_").uppercase()}'"
                            )
                        )
                    }
                }
            }
        }

        return issues
    }
}
