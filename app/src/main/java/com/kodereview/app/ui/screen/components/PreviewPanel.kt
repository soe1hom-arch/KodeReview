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
import androidx.compose.material.icons.filled.Window
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
            val composable = parser.parseForPreview()

            if (composable == null || composable.body.isEmpty()) {
                previewResult = PreviewResult.Empty(
                    "No @Composable function found.\nAdd @Composable fun YourScreen() { ... }"
                )
            } else {
                previewResult = PreviewResult.Success(
                    name = composable.name,
                    nodes = composable.body
                )
            }
        } catch (e: Exception) {
            previewResult = PreviewResult.Error(
                "Parse error: ${e.message ?: "Unknown error"}"
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
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = PrimaryColor,
                            strokeWidth = 2.dp
                        )
                    }
                }
                is PreviewResult.Empty -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = result.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariant
                        )
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
                            color = ErrorColor
                        )
                    }
                }
                is PreviewResult.Success -> {
                    // This is the actual live preview!
                    MaterialTheme(
                        colorScheme = MaterialTheme.colorScheme
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp)
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
            color = OnSurface
        )
        if (name != null) {
            Text(
                text = "· $name()",
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceVariant
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = when (result) {
                is PreviewResult.Success -> "${result.nodes.size} node(s)"
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
    data class Success(val name: String, val nodes: List<UiNode>) : PreviewResult()
    data class Empty(val message: String) : PreviewResult()
    data class Error(val message: String) : PreviewResult()
}
