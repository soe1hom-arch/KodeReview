package com.kodereview.app.analyzer.rule

import com.kodereview.app.analyzer.AnalysisIssue

interface Rule {
    val id: String
    val name: String
    val description: String
    fun analyze(source: String, lines: List<String>): List<AnalysisIssue>
}
