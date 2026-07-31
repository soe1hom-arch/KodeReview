package com.kodereview.app.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kodereview.app.analyzer.AnalyzerEngine
import com.kodereview.app.model.EditorUiState
import com.kodereview.app.model.KotlinProjectIndex
import com.kodereview.app.model.ProjectFile
import com.kodereview.app.model.ReferenceFile
import com.kodereview.app.model.SampleCode
import com.kodereview.app.ui.screen.components.CodeEditor
import com.kodereview.app.ui.screen.components.DiagnosticPanel
import com.kodereview.app.ui.screen.components.PreviewPanel
import com.kodereview.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    key: Int = 0,
    initialCode: String? = null,
    onPickFile: (() -> Unit)? = null,
    extraFiles: List<ReferenceFile> = emptyList(),
    onAddReferenceFiles: (() -> Unit)? = null,
    onRemoveReferenceFile: ((Int) -> Unit)? = null,
    projectFiles: List<ProjectFile> = emptyList(),
    folderStatus: String? = null,
    onPickFolder: (() -> Unit)? = null,
    onOpenProjectFile: ((ProjectFile) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val analyzerEngine = remember { AnalyzerEngine() }
    val defaultCode = initialCode ?: SampleCode.defaultSample

    var textFieldValue by remember(key) { mutableStateOf(TextFieldValue(defaultCode)) }
    var uiState by remember(key) { mutableStateOf(EditorUiState(code = defaultCode)) }
    var showDiagnostics by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableStateOf(0) }
    var analysisCounter by remember { mutableStateOf(0) }
    var autoFiles by remember(projectFiles) { mutableStateOf<List<ReferenceFile>>(emptyList()) }
    val context = LocalContext.current

    // Auto-resolve: load theme/component files referenced by the current code's imports.
    LaunchedEffect(textFieldValue.text, projectFiles) {
        autoFiles = if (projectFiles.isEmpty()) {
            emptyList()
        } else {
            // Debounce so we don't re-read files on every keystroke.
            delay(400)
            withContext(Dispatchers.IO) {
                KotlinProjectIndex.resolveReferences(context, textFieldValue.text, projectFiles)
            }
        }
    }

    // Run analysis when code changes
    LaunchedEffect(textFieldValue.text) {
        if (textFieldValue.text.isNotEmpty()) {
            uiState = uiState.copy(code = textFieldValue.text, isAnalyzing = true)
            delay(500)
            val issues = withContext(Dispatchers.Default) {
                analyzerEngine.analyze(textFieldValue.text)
            }
            uiState = uiState.copy(issues = issues, isAnalyzing = false)
            analysisCounter++
            showDiagnostics = true
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("KodeReview", color = OnSurface)
                        Spacer(Modifier.width(8.dp))
                        if (uiState.isAnalyzing) {
                            Text("⚙", color = PrimaryColor, fontSize = 16.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = SurfaceVariant,
                    titleContentColor = OnSurface
                ),
                actions = {
                    // Issue count badge
                    if (uiState.totalIssues > 0 && !uiState.isAnalyzing) {
                        val badgeColor = when {
                            uiState.errorCount > 0 -> ErrorColor
                            uiState.warningCount > 0 -> WarningColor
                            else -> InfoColor
                        }
                        Box(
                            modifier = Modifier
                                .background(
                                    color = badgeColor.copy(alpha = 0.15f),
                                    shape = MaterialTheme.shapes.small
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${uiState.totalIssues} issue${if (uiState.totalIssues != 1) "s" else ""}",
                                color = badgeColor,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                    }

                    // Diagnostic toggle
                    if (uiState.totalIssues > 0) {
                        IconButton(onClick = { showDiagnostics = !showDiagnostics }) {
                            Icon(
                                if (showDiagnostics) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                "Toggle issues",
                                tint = OnSurfaceVariant
                            )
                        }
                    }

                    if (onPickFile != null) {
                        IconButton(onClick = onPickFile) {
                            Icon(Icons.Default.FolderOpen, "Open .kt", tint = OnSurfaceVariant)
                        }
                    }
                    if (onAddReferenceFiles != null) {
                        IconButton(onClick = onAddReferenceFiles) {
                            Icon(Icons.Default.Add, "Tambah file referensi", tint = OnSurfaceVariant)
                        }
                    }
                    if (onPickFolder != null) {
                        IconButton(onClick = onPickFolder) {
                            Icon(Icons.Default.Folder, "Buka folder proyek", tint = OnSurfaceVariant)
                        }
                    }
                    IconButton(onClick = { textFieldValue = TextFieldValue(SampleCode.defaultSample) }) {
                        Icon(Icons.Default.Refresh, "Reset", tint = OnSurfaceVariant)
                    }
                    IconButton(onClick = { textFieldValue = TextFieldValue("") }) {
                        Icon(Icons.Default.Delete, "Clear", tint = OnSurfaceVariant)
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(EditorBackground)
        ) {
            // Tab Row
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = EditorBackground,
                contentColor = PrimaryColor,
                divider = { Divider(color = SurfaceVariant, thickness = 1.dp) }
            ) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                    text = { Text("Code", color = if (selectedTab == 0) PrimaryColor else OnSurfaceVariant) },
                    icon = { Icon(Icons.Default.Code, null, modifier = Modifier.size(18.dp), tint = if (selectedTab == 0) PrimaryColor else OnSurfaceVariant) }
                )
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                    text = { Text("Preview", color = if (selectedTab == 1) PrimaryColor else OnSurfaceVariant) },
                    icon = { Icon(Icons.Default.PhoneAndroid, null, modifier = Modifier.size(18.dp), tint = if (selectedTab == 1) PrimaryColor else OnSurfaceVariant) }
                )
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 },
                    text = { Text("Split", color = if (selectedTab == 2) PrimaryColor else OnSurfaceVariant) },
                    icon = { Icon(Icons.Default.ViewColumn, null, modifier = Modifier.size(18.dp), tint = if (selectedTab == 2) PrimaryColor else OnSurfaceVariant) }
                )
            }

            // Reference files row (auto-resolved + manual)
            if (extraFiles.isNotEmpty() || autoFiles.isNotEmpty() || folderStatus != null || projectFiles.isNotEmpty() || onAddReferenceFiles != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceVariant.copy(alpha = 0.4f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    folderStatus?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📂 ", fontSize = 10.sp)
                            Text(
                                it,
                                fontSize = 10.sp,
                                color = OnSurfaceVariant,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    }
                    if (projectFiles.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "File:",
                                fontSize = 10.sp,
                                color = PrimaryColor,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                            projectFiles.forEach { file ->
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(PrimaryColor.copy(alpha = 0.12f))
                                        .clickable { onOpenProjectFile?.invoke(file) }
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        file.relativePath,
                                        fontSize = 10.sp,
                                        color = OnSurface,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                    if (autoFiles.isNotEmpty() || extraFiles.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (autoFiles.isNotEmpty()) {
                                Text(
                                    "Auto:",
                                    fontSize = 10.sp,
                                    color = InfoColor,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                                autoFiles.forEach { file ->
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(InfoColor.copy(alpha = 0.12f))
                                            .padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            file.name,
                                            fontSize = 10.sp,
                                            color = OnSurface,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                            if (extraFiles.isNotEmpty()) {
                                Text(
                                    "Manual:",
                                    fontSize = 10.sp,
                                    color = WarningColor,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                                extraFiles.forEachIndexed { index, file ->
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(OnSurfaceVariant.copy(alpha = 0.15f))
                                            .clickable { onRemoveReferenceFile?.invoke(index) }
                                            .padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            file.name,
                                            fontSize = 10.sp,
                                            color = OnSurface,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            maxLines = 1
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("✕", fontSize = 9.sp, color = OnSurfaceVariant)
                                    }
                                }
                            }
                            if (onAddReferenceFiles != null) {
                                TextButton(
                                    onClick = onAddReferenceFiles,
                                    contentPadding = PaddingValues(horizontal = 6.dp),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Text("+ File", fontSize = 10.sp, color = PrimaryColor)
                                }
                            }
                        }
                    }
                }
            }

            // Tab content area
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (selectedTab) {
                    0 -> CodeEditor(
                        textFieldValue = textFieldValue,
                        onValueChange = { textFieldValue = it },
                        issues = uiState.issues,
                        onIssueClick = { uiState = uiState.copy(selectedIssue = it); showDiagnostics = true },
                        modifier = Modifier.fillMaxSize()
                    )
                    1 -> PreviewPanel(
                        sourceCode = textFieldValue.text,
                        extraSources = (autoFiles + extraFiles).distinctBy { it.name }.map { it.content },
                        modifier = Modifier.fillMaxSize()
                    )
                    2 -> Column(Modifier.fillMaxSize()) {
                        CodeEditor(
                            textFieldValue = textFieldValue,
                            onValueChange = { textFieldValue = it },
                            issues = uiState.issues,
                            onIssueClick = { uiState = uiState.copy(selectedIssue = it); showDiagnostics = true },
                            modifier = Modifier.fillMaxWidth().weight(1f)
                        )
                        Divider(color = SurfaceVariant, thickness = 2.dp)
                        PreviewPanel(
                            sourceCode = textFieldValue.text,
                            extraSources = (autoFiles + extraFiles).distinctBy { it.name }.map { it.content },
                            modifier = Modifier.fillMaxWidth().weight(0.7f)
                        )
                    }
                }
            }

            // Status bar with issue count
            if (uiState.isAnalyzing) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PrimaryColor.copy(alpha = 0.1f))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⚙", color = PrimaryColor, fontSize = 12.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Analyzing...",
                        color = PrimaryColor,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            } else if (uiState.totalIssues == 0 && textFieldValue.text.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(InfoColor.copy(alpha = 0.08f))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("✓", color = InfoColor, fontSize = 12.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "No issues found",
                        color = InfoColor,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            // Diagnostic panel
            AnimatedVisibility(
                visible = showDiagnostics && uiState.totalIssues > 0 && !uiState.isAnalyzing,
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
                        selectedTab = 0
                    },
                    onDismiss = { showDiagnostics = false },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
