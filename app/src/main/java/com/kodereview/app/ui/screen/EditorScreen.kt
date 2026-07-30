package com.kodereview.app.ui.screen

import androidx.compose.animation.*
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    key: Int = 0,
    initialCode: String? = null,
    onPickFile: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val analyzerEngine = remember { AnalyzerEngine() }

    // Use the initial code if provided (from file picker), otherwise use sample
    val defaultCode = initialCode ?: SampleCode.defaultSample
    var textFieldValue by remember(key) { mutableStateOf(TextFieldValue(defaultCode)) }
    var uiState by remember(key) { mutableStateOf(EditorUiState(code = defaultCode)) }
    var showDiagnostics by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableStateOf(EditorTab.SPLIT) }

    // Run analysis when code changes
    LaunchedEffect(textFieldValue.text) {
        uiState = uiState.copy(code = textFieldValue.text, isAnalyzing = true)
        delay(500)
        val issues = analyzerEngine.analyze(textFieldValue.text)
        uiState = uiState.copy(issues = issues, isAnalyzing = false)
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "KodeReview",
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (uiState.isAnalyzing) {
                                Spacer(Modifier.width(8.dp))
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = PrimaryColor
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = SurfaceVariant,
                        titleContentColor = OnSurface
                    ),
                    actions = {
                        if (onPickFile != null) {
                            IconButton(onClick = onPickFile) {
                                Icon(
                                    Icons.Default.FolderOpen,
                                    contentDescription = "Open .kt file",
                                    tint = OnSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = {
                            textFieldValue = TextFieldValue(SampleCode.defaultSample)
                        }) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Reset sample",
                                tint = OnSurfaceVariant
                            )
                        }
                        IconButton(onClick = {
                            textFieldValue = TextFieldValue("")
                        }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Clear",
                                tint = OnSurfaceVariant
                            )
                        }
                    }
                )

                // Tab: Code | Preview | Split
                TabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = EditorBackground,
                    contentColor = PrimaryColor,
                    divider = { Divider(color = SurfaceVariant) }
                ) {
                    Tab(
                        selected = selectedTab == EditorTab.CODE,
                        onClick = { selectedTab = EditorTab.CODE },
                        text = { Text("Code", style = MaterialTheme.typography.labelMedium) },
                        icon = {
                            Icon(
                                Icons.Default.Code,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == EditorTab.PREVIEW,
                        onClick = { selectedTab = EditorTab.PREVIEW },
                        text = { Text("Preview", style = MaterialTheme.typography.labelMedium) },
                        icon = {
                            Icon(
                                Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == EditorTab.SPLIT,
                        onClick = { selectedTab = EditorTab.SPLIT },
                        text = { Text("Split", style = MaterialTheme.typography.labelMedium) },
                        icon = {
                            Icon(
                                Icons.Default.Window,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }
        },
        bottomBar = {
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
                        val lines = textFieldValue.text.lines()
                        var cursorPos = 0
                        for (i in 0 until issue.line.coerceIn(0, lines.size - 1)) {
                            cursorPos += lines[i].length + 1
                        }
                        cursorPos += issue.column
                        textFieldValue = textFieldValue.copy(
                            selection = TextRange(cursorPos, cursorPos)
                        )
                        selectedTab = EditorTab.CODE
                    },
                    onDismiss = { showDiagnostics = false },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        modifier = modifier
    ) { paddingValues ->
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
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
            EditorTab.PREVIEW -> {
                PreviewPanel(
                    sourceCode = textFieldValue.text,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
            EditorTab.SPLIT -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    CodeEditor(
                        textFieldValue = textFieldValue,
                        onValueChange = { textFieldValue = it },
                        issues = uiState.issues,
                        onIssueClick = { issue ->
                            uiState = uiState.copy(selectedIssue = issue)
                            showDiagnostics = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )

                    Divider(
                        color = SurfaceVariant,
                        thickness = 2.dp
                    )

                    PreviewPanel(
                        sourceCode = textFieldValue.text,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.7f)
                    )
                }
            }
        }
    }
}
