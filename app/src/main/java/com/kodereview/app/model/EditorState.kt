package com.kodereview.app.model

import com.kodereview.app.analyzer.AnalysisIssue
import com.kodereview.app.analyzer.Severity

data class EditorUiState(
    val code: String = "",
    val issues: List<AnalysisIssue> = emptyList(),
    val isAnalyzing: Boolean = false,
    val cursorLine: Int = 0,
    val selectedIssue: AnalysisIssue? = null
) {
    val errorCount: Int get() = issues.count { it.severity == Severity.ERROR }
    val warningCount: Int get() = issues.count { it.severity == Severity.WARNING }
    val infoCount: Int get() = issues.count { it.severity == Severity.INFO || it.severity == Severity.SUGGESTION }
    val totalIssues: Int get() = issues.size
}
