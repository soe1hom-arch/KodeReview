package com.kodereview.app.analyzer

import com.kodereview.app.analyzer.rule.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AnalyzerEngine {

    private val rules: List<Rule> = listOf(
        ComposableNamingRule(),
        ModifierOrderRule(),
        RememberUsageRule(),
        StateHoistingRule(),
        NamingConventionRule(),
        ExpressionBodyRule(),
        UnusedImportRule()
    )

    /**
     * Run full analysis on the source code.
     * Returns a list of issues sorted by line number.
     */
    suspend fun analyze(source: String): List<AnalysisIssue> = withContext(Dispatchers.Default) {
        if (source.isBlank()) return@withContext emptyList()

        val lines = source.lines()
        val allIssues = mutableListOf<AnalysisIssue>()

        for (rule in rules) {
            try {
                val issues = rule.analyze(source, lines)
                allIssues.addAll(issues)
            } catch (e: Exception) {
                // Rule failed silently - don't crash the analyzer
                android.util.Log.w("KodeReview", "Rule ${rule.id} failed: ${e.message}")
            }
        }

        // Sort by line, then column
        allIssues.sortedWith(compareBy({ it.line }, { it.column }))
    }
}
