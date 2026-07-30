package com.kodereview.app.analyzer.rule

import com.kodereview.app.analyzer.AnalysisIssue
import com.kodereview.app.analyzer.Severity

class StateHoistingRule : Rule {
    override val id = "StateHoisting"
    override val name = "State Hoisting"
    override val description = "Suggest state hoisting for reusable composables"

    override fun analyze(source: String, lines: List<String>): List<AnalysisIssue> {
        val issues = mutableListOf<AnalysisIssue>()
        val text = source

        // Detect composable functions that create their own state
        val composableRegex = Regex("""@Composable\s*\n\s*fun\s+(\w+)""")
        val stateRegex = Regex("""(?:var|val)\s+(\w+)\s*(?:by\s+)?(?:remember|mutableStateOf)""")

        for (composableMatch in composableRegex.findAll(text)) {
            val funcName = composableMatch.groupValues[1]
            val funcStart = composableMatch.range.first
            val funcEnd = findMatchingBrace(text, funcStart)
            val funcBody = if (funcEnd > 0) text.substring(funcStart, funcEnd) else ""

            // Check for state declarations in the body
            val stateDeclarations = stateRegex.findAll(funcBody).toList()

            // If the function has state and looks like a reusable component
            if (stateDeclarations.isNotEmpty() && funcName[0].isUpperCase() &&
                !funcName.contains("Screen") && !funcName.contains("Activity") &&
                !funcName.contains("Dialog") && !funcName.contains("BottomSheet")
            ) {
                for (stateMatch in stateDeclarations) {
                    val stateName = stateMatch.groupValues[1]
                    val lineInFunc = funcBody.substring(0, stateMatch.range.first).count { it == '\n' }
                    val absLine = text.substring(0, funcStart).count { it == '\n' } + lineInFunc

                    issues.add(
                        AnalysisIssue(
                            line = absLine,
                            column = stateMatch.range.first - funcBody.lastIndexOf('\n', stateMatch.range.first) - 1,
                            length = stateMatch.value.length,
                            severity = Severity.SUGGESTION,
                            message = "Consider hoisting '$stateName' state out of '$funcName' for reusability",
                            ruleId = id,
                            suggestion = "Move state to caller and pass as parameter"
                        )
                    )
                }
            }
        }

        return issues
    }

    private fun findMatchingBrace(text: String, start: Int): Int {
        var braceCount = 0
        var foundOpen = false
        for (i in start until text.length) {
            when (text[i]) {
                '{' -> {
                    braceCount++
                    foundOpen = true
                }
                '}' -> {
                    braceCount--
                    if (foundOpen && braceCount == 0) return i + 1
                }
            }
        }
        return -1
    }
}
