package com.kodereview.app.ui.screen.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Warning
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
import com.kodereview.app.ui.theme.*

/**
 * Live preview panel that parses and renders Compose UI from source code.
 */
@Composable
fun PreviewPanel(
    sourceCode: String,
    modifier: Modifier = Modifier
) {
    var previewResult by remember(sourceCode) {
        mutableStateOf<PreviewResult>(PreviewResult.Loading)
    }

    // Run parser in background
    LaunchedEffect(sourceCode) {
        if (sourceCode.isBlank()) {
            previewResult = PreviewResult.Empty("No code to preview")
            return@LaunchedEffect
        }

        try {
            val parser = ComposePreviewParser(sourceCode)
            val allComposables = parser.parseAll()
            val composable = parser.parseForPreview()

            if (allComposables.isEmpty()) {
                previewResult = PreviewResult.Empty(
                    "No @Composable function found in code.\n\n" +
                    "Make sure your code has:\n" +
                    "@Composable\n" +
                    "fun YourScreen() {\n" +
                    "    // Compose UI here\n" +
                    "}"
                )
            } else if (composable == null || composable.body.isEmpty()) {
                previewResult = PreviewResult.Empty(
                    "Composable '${allComposables.last().name}' found but no UI nodes parsed.\n" +
                    "Try adding Column, Text, Button, etc."
                )
            } else {
                val nodeCount = countNodes(composable.body)
                previewResult = PreviewResult.Success(
                    name = composable.name,
                    nodes = composable.body,
                    totalComposables = allComposables.size,
                    nodeCount = nodeCount
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("PreviewPanel", "Parse error", e)
            previewResult = PreviewResult.Error(
                "Parse error: ${e.message ?: "Unknown error"}\n\n" +
                "Check your code for syntax errors."
            )
        }
    }

    Column(modifier = modifier) {
        // Preview header
        PreviewHeader(previewResult)
        
        // Preview content
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
                            Text(
                                "Parsing...",
                                color = OnSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                is PreviewResult.Empty -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = OnSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = result.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariant,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
                is PreviewResult.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = ErrorColor,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = result.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = ErrorColor,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                is PreviewResult.Success -> {
                    Column(modifier = Modifier.padding(8.dp)) {
                        // Info badge
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(PrimaryColor.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "✓ Parsed ${result.nodeCount} node(s) from ${result.name}()",
                                color = PrimaryColor,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(8.dp))

                        // Render the UI
                        MaterialTheme(
                            colorScheme = MaterialTheme.colorScheme
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, SurfaceVariant, RoundedCornerShape(8.dp)),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Box(
                                    modifier = Modifier.padding(2.dp),
                                    contentAlignment = Alignment.TopStart
                                ) {
                                    ComposePreviewRenderer.Render(result.nodes)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
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
            is UiNode.LazyColumn -> count += countNodes(node.children)
            is UiNode.LazyRow -> count += countNodes(node.children)
            is UiNode.Scaffold -> {
                node.content?.let { count += countNodes(listOf(it)) }
            }
            else -> {}
        }
    }
    return count
}

private data class PreviewHeaderState(
    val name: String? = null,
    val showDeviceFrame: Boolean = true
)

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
        Icon(
            Icons.Default.PhoneAndroid,
            contentDescription = "Preview",
            tint = PrimaryColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "Live Preview",
            style = MaterialTheme.typography.labelMedium,
            color = OnSurface,
            fontWeight = FontWeight.Bold
        )
        if (name != null) {
            Text(
                text = " · $name()",
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceVariant
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = when (result) {
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
    data class Success(
        val name: String,
        val nodes: List<UiNode>,
        val totalComposables: Int = 1,
        val nodeCount: Int = 0
    ) : PreviewResult()
    data class Empty(val message: String) : PreviewResult()
    data class Error(val message: String) : PreviewResult()
}
