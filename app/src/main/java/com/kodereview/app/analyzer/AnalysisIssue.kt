package com.kodereview.app.analyzer

data class AnalysisIssue(
    val line: Int,           // 0-based line number
    val column: Int,         // 0-based column offset
    val length: Int,         // length of the problematic text
    val severity: Severity,
    val message: String,
    val ruleId: String,
    val suggestion: String? = null
)

enum class Severity {
    ERROR,
    WARNING,
    INFO,
    SUGGESTION
}
