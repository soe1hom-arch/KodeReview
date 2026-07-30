package com.kodereview.app.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.kodereview.app.analyzer.AnalyzerEngine
import com.kodereview.app.model.EditorUiState
import com.kodereview.app.model.SampleCode
import com.kodereview.app.ui.screen.components.CodeEditor
import com.kodereview.app.ui.screen.components.DiagnosticPanel
import com.kodereview.app.ui.screen.components.PreviewPanel
import com.kodereview.app.ui.theme.*
import kotlinx.coroutines.delay

private enum class EditorTab { CODE, PREVIEW, SPLIT }

@Composable
fun EditorScreen(
    key: Int = 0,
    initialCode: String? = null,
    onPickFile: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val analyzerEngine = remember { AnalyzerEngine() }
    val defaultCode = initialCode ?: SampleCode.defaultSample

    var textFieldValue by remember(key) { mutableStateOf(TextFieldValue(defaultCode)) }
    var uiState by remember(key) { mutableStateOf(EditorUiState(code = defaultCode)) }
    var showDiagnostics by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableStateOf(EditorTab.CODE) }

    LaunchedEffect(textFieldValue.text) {
        if (textFieldValue.text.isNotEmpty()) {
            uiState = uiState.copy(code = textFieldValue.text, isAnalyzing = true)
            delay(500)
            val issues = analyzerEngine.analyze(textFieldValue.text)
            uiState = uiState.copy(issues = issues, isAnalyzing = false)
        }
    }

    Column(modifier = modifier.fillMaxSize().background(EditorBackground)) {
        // Toolbar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = SurfaceVariant,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("KodeReview", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                if (uiState.isAnalyzing) {
                    Spacer(Modifier.width(6.dp))
                    Text("●", color = PrimaryColor, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                }
                Spacer(Modifier.weight(1f))
                if (onPickFile != null) {
                    IconButton(onClick = onPickFile, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.FolderOpen, "Open .kt", tint = OnSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                }
                IconButton(onClick = { textFieldValue = TextFieldValue(SampleCode.defaultSample) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Refresh, "Reset", tint = OnSurfaceVariant, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = { textFieldValue = TextFieldValue("") }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, "Clear", tint = OnSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }
        }

        // Tabs
        Row(
            modifier = Modifier.fillMaxWidth().background(EditorBackground),
            horizontalArrangement = Arrangement.Start
        ) {
            TabItem("Code", selectedTab == EditorTab.CODE) { selectedTab = EditorTab.CODE }
            TabItem("Preview", selectedTab == EditorTab.PREVIEW) { selectedTab = EditorTab.PREVIEW }
            TabItem("Split", selectedTab == EditorTab.SPLIT) { selectedTab = EditorTab.SPLIT }
        }

        Divider(color = SurfaceVariant, thickness = 1.dp)

        // Content area
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (selectedTab) {
                EditorTab.CODE -> {
                    CodeEditor(
                        textFieldValue = textFieldValue,
                        onValueChange = { textFieldValue = it },
                        issues = uiState.issues,
                        onIssueClick = { issue ->
                            uiState = uiState.copy(selectedIssue = issue)
                            showDiagnostics = true
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                EditorTab.PREVIEW -> {
                    PreviewPanel(
                        sourceCode = textFieldValue.text,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                EditorTab.SPLIT -> {
                    Column(Modifier.fillMaxSize().background(EditorBackground)) {
                        CodeEditor(
                            textFieldValue = textFieldValue,
                            onValueChange = { textFieldValue = it },
                            issues = uiState.issues,
                            onIssueClick = { issue ->
                                uiState = uiState.copy(selectedIssue = issue)
                                showDiagnostics = true
                            },
                            modifier = Modifier.fillMaxWidth().weight(1f)
                        )
                        Divider(color = SurfaceVariant, thickness = 2.dp)
                        PreviewPanel(
                            sourceCode = textFieldValue.text,
                            modifier = Modifier.fillMaxWidth().weight(0.7f)
                        )
                    }
                }
            }
        }

        // Diagnostic panel at bottom
        AnimatedVisibility(
            visible = showDiagnostics && uiState.totalIssues > 0 && selectedTab != EditorTab.PREVIEW,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            DiagnosticPanel(
                issues = uiState.issues,
                totalIssues = uiState.totalIssues,
                errorCount = uiState.errorCount,
                warningCount = uiState.warningCount,
                infoCount = uiState.infoCount,
                selectedIssue = uiState.selectedIssue,
                onIssueClick = { issue ->
                    uiState = uiState.copy(selectedIssue = issue)
                    if (textFieldValue.text.isNotEmpty()) {
                        val lines = textFieldValue.text.lines()
                        var cursorPos = 0
                        for (i in 0 until issue.line.coerceIn(0, lines.size - 1)) {
                            cursorPos += lines[i].length + 1
                        }
                        cursorPos += issue.column
                        textFieldValue = textFieldValue.copy(selection = TextRange(cursorPos))
                    }
                    selectedTab = EditorTab.CODE
                },
                onDismiss = { showDiagnostics = false },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun TabItem(text: String, selected: Boolean, onClick: () -> Unit) {
    val bgColor = if (selected) PrimaryColor.copy(alpha = 0.15f) else EditorBackground
    val textColor = if (selected) PrimaryColor else OnSurfaceVariant

    Box(
        modifier = Modifier
            .height(40.dp)
            .background(bgColor)
            .clickable { onClick() }
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = textColor)
    }
}
