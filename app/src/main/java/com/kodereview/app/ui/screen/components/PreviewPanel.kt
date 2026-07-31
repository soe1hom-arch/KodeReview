package com.kodereview.app.ui.screen.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kodereview.app.model.UiNode
import com.kodereview.app.preview.ComposePreviewRenderer
import com.kodereview.app.preview.ComposePreviewParser
import com.kodereview.app.preview.ParsedComposable
import com.kodereview.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val PARSE_TIMEOUT_MS = 4000L

/**
 * Live preview panel that parses and renders Compose UI from source code.
 * Parser runs on background thread with timeout to prevent ANR.
 */
@Composable
fun PreviewPanel(
    sourceCode: String,
    modifier: Modifier = Modifier
) {
    var previewResult by remember(sourceCode) {
        mutableStateOf<PreviewResult>(PreviewResult.Loading)
    }
    var composables by remember(sourceCode) {
        mutableStateOf<List<ParsedComposable>>(emptyList())
    }
    var selectedIndex by remember(sourceCode) {
        mutableStateOf(0)
    }

    // Run parser in background with timeout
    LaunchedEffect(sourceCode) {
        if (sourceCode.isBlank()) {
            previewResult = PreviewResult.Empty("No code to preview")
            composables = emptyList()
            return@LaunchedEffect
        }

        try {
            val result = withTimeoutOrNull(PARSE_TIMEOUT_MS) {
                withContext(Dispatchers.Default) {
                    // Pass cancellation check so parser can respond to timeout
                    val ctx = kotlin.coroutines.coroutineContext
                    val isActiveCheck = { ctx[kotlinx.coroutines.Job]?.isActive != false }
                    val parser = ComposePreviewParser(sourceCode, isActiveCheck, PARSE_TIMEOUT_MS)
                    val allComposables = parser.parseAll()
                    val preview = allComposables.firstOrNull { it.hasPreviewAnnotation }
                        ?: allComposables.firstOrNull()
                    Pair(allComposables, preview)
                }
            }

            if (result == null) {
                // Timeout occurred
                previewResult = PreviewResult.Error(
                    "Parse timeout (>${PARSE_TIMEOUT_MS / 1000}s)\n" +
                    "Code may be too complex for preview."
                )
                composables = emptyList()
                return@LaunchedEffect
            }

            val (allComposables, composable) = result

            if (allComposables.isEmpty()) {
                previewResult = PreviewResult.Empty(
                    "No @Composable function found.\n\n" +
                    "Add:\n" +
                    "@Composable\n" +
                    "fun YourScreen() { ... }"
                )
                composables = emptyList()
            } else {
                composables = allComposables
                val previewIdx = allComposables.indexOfFirst { it.hasPreviewAnnotation }
                    .takeIf { it != -1 }
                    ?: 0
                selectedIndex = previewIdx
                val chosen = allComposables[previewIdx]
                if (chosen.body.isEmpty()) {
                    previewResult = PreviewResult.Empty(
                        "'${chosen.name}' found but no UI nodes.\n" +
                        "Try adding Column, Text, Button, etc."
                    )
                } else {
                    val nodeCount = countNodes(chosen.body)
                    previewResult = PreviewResult.Success(
                        name = chosen.name,
                        nodes = chosen.body,
                        totalComposables = allComposables.size,
                        nodeCount = nodeCount
                    )
                }
            }
        } catch (e: CancellationException) {
            // If the effect itself is still active, the parse was aborted by the
            // internal timeout machinery. Show an error instead of hanging forever.
            if (kotlin.coroutines.coroutineContext[Job]?.isActive != false) {
                previewResult = PreviewResult.Error(
                    "Parse timed out (>${PARSE_TIMEOUT_MS / 1000}s)"
                )
            } else {
                throw e
            }
        } catch (e: Exception) {
            android.util.Log.e("PreviewPanel", "Parse error", e)
            previewResult = PreviewResult.Error(
                "Parse error: ${e.message ?: "Unknown"}"
            )
        }
    }

    Column(modifier = modifier) {
        PreviewHeader(previewResult)

        // Composable selector
        if (composables.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(EditorBackground)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                composables.forEachIndexed { index, c ->
                    val selected = index == selectedIndex
                    OutlinedButton(
                        onClick = {
                            selectedIndex = index
                            val nodeCount = countNodes(c.body)
                            previewResult = if (c.body.isEmpty()) {
                                PreviewResult.Empty("'${c.name}' found but no UI nodes.")
                            } else {
                                PreviewResult.Success(
                                    name = c.name,
                                    nodes = c.body,
                                    totalComposables = composables.size,
                                    nodeCount = nodeCount
                                )
                            }
                        },
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        border = BorderStroke(
                            1.dp,
                            if (selected) PrimaryColor else SurfaceVariant
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selected) PrimaryColor.copy(alpha = 0.12f)
                                else androidx.compose.ui.graphics.Color.Transparent
                        )
                    ) {
                        Text(
                            c.name,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) PrimaryColor else OnSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(EditorBackground)
                .verticalScroll(rememberScrollState())
                .padding(4.dp)
        ) {
            when (val result = previewResult) {
                is PreviewResult.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("⟳", color = PrimaryColor, fontSize = 24.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("Parsing…", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
                is PreviewResult.Empty -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("📄", fontSize = 28.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(result.message, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant, fontFamily = FontFamily.Monospace)
                    }
                }
                is PreviewResult.Error -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Warning, null, tint = ErrorColor, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(result.message, style = MaterialTheme.typography.bodySmall, color = ErrorColor, fontFamily = FontFamily.Monospace)
                    }
                }
                is PreviewResult.Success -> {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(PrimaryColor.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "✓ ${result.nodeCount} node(s) from ${result.name}()",
                                color = PrimaryColor,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        RenderPreviewTree(result.nodes, result.name)
                    }
                }
            }
        }
    }
}

/**
 * Renders the parsed node tree with a safety net: if rendering throws, show
 * a text fallback of the node tree instead of a blank/crashed panel.
 */
@Composable
private fun RenderPreviewTree(nodes: List<UiNode>, name: String) {
    var showTree by remember(nodes, name) { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = { showTree = !showTree }) {
                Text(
                    if (showTree) "◉ Render" else "☰ Tree",
                    style = MaterialTheme.typography.labelSmall,
                    color = PrimaryColor
                )
            }
        }
        if (showTree) {
            Text(
                treeDump(nodes),
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        } else {
            MaterialTheme(colorScheme = MaterialTheme.colorScheme) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, SurfaceVariant, RoundedCornerShape(8.dp)),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Box(modifier = Modifier.padding(2.dp), contentAlignment = Alignment.TopStart) {
                        ComposePreviewRenderer.Render(nodes)
                    }
                }
            }
        }
    }
}

private fun treeDump(nodes: List<UiNode>, indent: String = ""): String {
    val sb = StringBuilder()
    for (node in nodes) {
        val label = when (node) {
            is UiNode.Column -> "Column"
            is UiNode.Row -> "Row"
            is UiNode.Box -> "Box"
            is UiNode.Surface -> "Surface"
            is UiNode.Card -> "Card"
            is UiNode.Text -> "Text(\"${node.text.take(40).replace("\"", "'")}\")"
            is UiNode.Scaffold -> "Scaffold"
            is UiNode.ModalNavigationDrawer -> "ModalNavigationDrawer"
            is UiNode.ModalDrawerSheet -> "ModalDrawerSheet"
            is UiNode.NavigationBar -> "NavigationBar"
            is UiNode.NavigationBarItem -> "NavigationBarItem(\"${node.label.take(20)}\")"
            is UiNode.TopAppBar -> "TopAppBar(\"${node.title.take(20)}\")"
            is UiNode.Unknown -> "Unknown(${node.name})"
            else -> node::class.simpleName ?: "?"
        }
        sb.append("$indent$label\n")
        when (node) {
            is UiNode.Column -> sb.append(treeDump(node.children, indent + "  "))
            is UiNode.Row -> sb.append(treeDump(node.children, indent + "  "))
            is UiNode.Box -> sb.append(treeDump(node.children, indent + "  "))
            is UiNode.Surface -> sb.append(treeDump(node.children, indent + "  "))
            is UiNode.Card -> sb.append(treeDump(node.children, indent + "  "))
            is UiNode.Scaffold -> {
                listOfNotNull(node.topBar, node.bottomBar, node.content).forEach {
                    sb.append(treeDump(listOf(it), indent + "  "))
                }
            }
            is UiNode.ModalNavigationDrawer -> {
                listOfNotNull(node.drawerContent, node.content).forEach {
                    sb.append(treeDump(listOf(it), indent + "  "))
                }
            }
            is UiNode.ModalDrawerSheet -> sb.append(treeDump(node.children, indent + "  "))
            is UiNode.NavigationBar -> sb.append(treeDump(node.children, indent + "  "))
            is UiNode.Unknown -> sb.append(treeDump(node.children, indent + "  "))
            is UiNode.LazyColumn -> sb.append(treeDump(node.items, indent + "  "))
            is UiNode.LazyRow -> sb.append(treeDump(node.items, indent + "  "))
            is UiNode.Dialog -> sb.append(treeDump(node.children, indent + "  "))
            else -> {}
        }
    }
    return sb.toString()
}

private fun countNodes(nodes: List<UiNode>): Int {
    var count = 0
    for (node in nodes) {
        count++
        when (node) {
            is UiNode.Column -> count += countNodes(node.children)
            is UiNode.Row -> count += countNodes(node.children)
            is UiNode.Box -> count += countNodes(node.children)
            is UiNode.Surface -> count += countNodes(node.children)
            is UiNode.Card -> count += countNodes(node.children)
            is UiNode.LazyColumn -> count += countNodes(node.items)
            is UiNode.LazyRow -> count += countNodes(node.items)
            is UiNode.Scaffold -> {
                listOfNotNull(node.topBar, node.bottomBar, node.content).forEach {
                    count += countNodes(listOf(it))
                }
            }
            is UiNode.NavigationBar -> count += countNodes(node.children)
            is UiNode.ModalDrawerSheet -> count += countNodes(node.children)
            is UiNode.Dialog -> count += countNodes(node.children)
            is UiNode.ModalNavigationDrawer -> {
                listOfNotNull(node.drawerContent, node.content).forEach {
                    count += countNodes(listOf(it))
                }
            }
            is UiNode.Unknown -> count += countNodes(node.children)
            else -> {}
        }
    }
    return count
}

@Composable
private fun PreviewHeader(result: PreviewResult) {
    val name = when (result) {
        is PreviewResult.Success -> result.name
        else -> null
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceVariant)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.PhoneAndroid, "Preview", tint = PrimaryColor, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Live Preview", style = MaterialTheme.typography.labelMedium, color = OnSurface, fontWeight = FontWeight.Bold)
        name?.let { Text(" · $it()", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant) }
        Spacer(Modifier.weight(1f))
        Text(
            when (result) {
                is PreviewResult.Success -> "${result.nodeCount} node(s)"
                is PreviewResult.Empty -> ""
                is PreviewResult.Error -> "Error"
                is PreviewResult.Loading -> ""
            },
            style = MaterialTheme.typography.labelSmall,
            color = OnSurfaceVariant
        )
    }
}

private sealed class PreviewResult {
    object Loading : PreviewResult()
    data class Success(val name: String, val nodes: List<UiNode>, val totalComposables: Int = 1, val nodeCount: Int = 0) : PreviewResult()
    data class Empty(val message: String) : PreviewResult()
    data class Error(val message: String) : PreviewResult()
}
