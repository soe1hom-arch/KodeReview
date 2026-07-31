package com.kodereview.app.ui.screen.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    extraSources: List<String> = emptyList(),
    modifier: Modifier = Modifier
) {
    // Merge the main file with reference files so cross-file composables
    // (components, theme colors) can be inlined into one preview.
    val mergedSource = remember(sourceCode, extraSources) {
        if (extraSources.isEmpty()) {
            sourceCode
        } else {
            buildString {
                append(sourceCode)
                if (sourceCode.isBlank() || sourceCode.lastOrNull() != '\n') append('\n')
                extraSources.forEach { extra ->
                    append("\n// ===== reference file =====\n")
                    append(extra)
                    append('\n')
                }
            }
        }
    }
    var previewResult by remember(mergedSource) {
        mutableStateOf<PreviewResult>(PreviewResult.Loading)
    }
    var composables by remember(mergedSource) {
        mutableStateOf<List<ParsedComposable>>(emptyList())
    }
    var selectedIndex by remember(mergedSource) {
        mutableStateOf(0)
    }
    var showTree by remember(mergedSource) {
        mutableStateOf(false)
    }
    var themeColors by remember(mergedSource) {
        mutableStateOf<Map<String, String>>(emptyMap())
    }

    // Run parser in background with timeout
    LaunchedEffect(mergedSource) {
        if (mergedSource.isBlank()) {
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
                    val parser = ComposePreviewParser(mergedSource, isActiveCheck, PARSE_TIMEOUT_MS)
                    val allComposables = parser.parseAll()
                    val preview = allComposables.firstOrNull { it.hasPreviewAnnotation }
                        ?: allComposables.firstOrNull()
                    Triple(allComposables, preview, parser.colorMap())
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

            val (allComposables, composable, colors) = result
            themeColors = colors

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
        // Header bar: title + composable selector + tree toggle. The preview
        // area below it is left clean, full-size and scrollable.
        PreviewHeader(
            result = previewResult,
            composables = composables,
            selectedIndex = selectedIndex,
            showTree = showTree,
            onSelectComposable = { index ->
                selectedIndex = index
                val c = composables[index]
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
            onToggleTree = { showTree = !showTree }
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(EditorBackground)
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
                    val baseScheme = MaterialTheme.colorScheme
                    val previewScheme = remember(themeColors, baseScheme) {
                        buildPreviewColorScheme(themeColors, baseScheme)
                    }
                    if (showTree) {
                        Text(
                            treeDump(result.nodes),
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(8.dp)
                        )
                    } else {
                        RenderPreviewTree(result.nodes, themeColors, previewScheme)
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
private fun RenderPreviewTree(
    nodes: List<UiNode>,
    colors: Map<String, String>,
    scheme: ColorScheme
) {
    try {
        MaterialTheme(colorScheme = scheme) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, SurfaceVariant, RoundedCornerShape(8.dp)),
                color = MaterialTheme.colorScheme.surface
            ) {
                Box(modifier = Modifier.padding(2.dp), contentAlignment = Alignment.TopStart) {
                    ComposePreviewRenderer.Render(nodes, colors)
                }
            }
        }
    } catch (e: Exception) {
        Text(
            "Preview render error: ${e.message}\n\n" + treeDump(nodes),
            style = MaterialTheme.typography.labelSmall,
            color = ErrorColor,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(8.dp)
        )
    }
}

/**
 * Parses a color literal expression like `Color(0xFF20E39B)` or `0xFF20E39B`.
 * Returns null when the expression does not contain a hex color.
 */
private fun parseColorLiteral(expr: String): Color? {
    val match = Regex("""0[xX]([0-9a-fA-F]{6,8})""").find(expr) ?: return null
    val hex = match.groupValues[1]
    val value = hex.toLong(16)
    return if (hex.length == 8) {
        Color(value)
    } else {
        Color(0xFF000000L or value)
    }
}

/**
 * Synthesizes a Material color scheme from the theme colors extracted from the
 * previewed source (e.g. AFFT's Green500 / DarkBackground / Red500), so that
 * `MaterialTheme.colorScheme.*` references and component defaults render with
 * the app's real colors instead of KodeReview's own theme.
 */
private fun buildPreviewColorScheme(
    colors: Map<String, String>,
    def: ColorScheme
): ColorScheme {
    if (colors.isEmpty()) return def
    fun pick(vararg keys: String): Color? =
        keys.firstNotNullOfOrNull { key -> colors[key]?.let(::parseColorLiteral) }

    return darkColorScheme(
        primary = pick("Green500", "PrimaryColor", "Primary", "ColorPrimary") ?: def.primary,
        secondary = pick("Cyan500", "SecondaryColor", "Secondary") ?: def.secondary,
        tertiary = pick("Yellow500", "TertiaryColor", "Tertiary") ?: def.tertiary,
        background = pick("DarkBackground", "BackgroundColor", "EditorBackground", "Background") ?: def.background,
        surface = pick("DarkSurface", "SurfaceColor", "Surface") ?: def.surface,
        surfaceVariant = pick("DarkSurface2", "SurfaceVariant", "TerminalBackground", "SurfaceColorVariant") ?: def.surfaceVariant,
        error = pick("Red500", "ErrorColor", "TerminalError") ?: def.error,
        onPrimary = pick("OnPrimary", "White") ?: def.onPrimary,
        onSecondary = pick("OnSecondary", "White") ?: def.onSecondary,
        onBackground = pick("OnBackground", "White") ?: def.onBackground,
        onSurface = pick("OnSurface", "TerminalText", "White") ?: def.onSurface,
        onSurfaceVariant = pick("OnSurfaceVariant", "TerminalInfo", "White") ?: def.onSurfaceVariant,
        onError = pick("OnError", "White") ?: def.onError,
        outline = pick("Outline", "Green700") ?: def.outline
    )
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
                node.dialogs.forEach {
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
                node.dialogs.forEach { count += countNodes(listOf(it)) }
            }
            is UiNode.Unknown -> count += countNodes(node.children)
            else -> {}
        }
    }
    return count
}

@Composable
private fun PreviewHeader(
    result: PreviewResult,
    composables: List<ParsedComposable>,
    selectedIndex: Int,
    showTree: Boolean,
    onSelectComposable: (Int) -> Unit,
    onToggleTree: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.PhoneAndroid, "Preview", tint = PrimaryColor, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Live Preview", style = MaterialTheme.typography.labelMedium, color = OnSurface, fontWeight = FontWeight.Bold)
        val name = when (result) {
            is PreviewResult.Success -> result.name
            else -> null
        }
        name?.let { Text(" \u00b7 $it()", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant, maxLines = 1) }
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
        if (result is PreviewResult.Success && composables.size > 1) {
            Spacer(Modifier.width(8.dp))
            var menuExpanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.height(30.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    border = BorderStroke(1.dp, PrimaryColor.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = PrimaryColor.copy(alpha = 0.12f)
                    )
                ) {
                    Text(
                        composables.getOrNull(selectedIndex)?.name ?: "-",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryColor,
                        maxLines = 1
                    )
                    Spacer(Modifier.width(3.dp))
                    Text("\u25BE", fontSize = 9.sp, color = PrimaryColor)
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    containerColor = EditorBackground
                ) {
                    composables.forEachIndexed { index, c ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    c.name,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onSelectComposable(index)
                            }
                        )
                    }
                }
            }
        }
        if (result is PreviewResult.Success) {
            TextButton(onClick = onToggleTree, contentPadding = PaddingValues(horizontal = 6.dp)) {
                Text(
                    if (showTree) "\u25c9 Render" else "\u2630 Tree",
                    style = MaterialTheme.typography.labelSmall,
                    color = PrimaryColor
                )
            }
        }
    }
}

private sealed class PreviewResult {
    object Loading : PreviewResult()
    data class Success(val name: String, val nodes: List<UiNode>, val totalComposables: Int = 1, val nodeCount: Int = 0) : PreviewResult()
    data class Empty(val message: String) : PreviewResult()
    data class Error(val message: String) : PreviewResult()
}
