package com.kodereview.app.preview

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kodereview.app.model.*

/**
 * Renders a parsed UiNode tree into actual Jetpack Compose UI components.
 */
object ComposePreviewRenderer {

    /**
     * Render a list of UiNodes as a Column (the default container).
     */
    @Composable
    fun Render(nodes: List<UiNode>): Unit {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            nodes.forEach { node ->
                RenderNode(node)
            }
        }
    }

    /**
     * Render a single UiNode.
     */
    @Composable
    fun RenderNode(node: UiNode) {
        when (node) {
            is UiNode.Column -> RenderColumn(node)
            is UiNode.Row -> RenderRow(node)
            is UiNode.Box -> RenderBox(node)
            is UiNode.Text -> RenderText(node)
            is UiNode.Button -> RenderButton(node)
            is UiNode.OutlinedButton -> RenderOutlinedButton(node)
            is UiNode.TextButton -> RenderTextButton(node)
            is UiNode.Icon -> RenderIcon(node)
            is UiNode.Image -> RenderImage(node)
            is UiNode.Spacer -> RenderSpacer(node)
            is UiNode.Divider -> RenderDivider(node)
            is UiNode.CircularProgressIndicator -> RenderCircularProgress(node)
            is UiNode.LinearProgressIndicator -> RenderLinearProgress(node)
            is UiNode.Surface -> RenderSurface(node)
            is UiNode.Card -> RenderCard(node)
            is UiNode.LazyColumn -> RenderLazyColumn(node)
            is UiNode.LazyRow -> RenderLazyRow(node)
            is UiNode.Scaffold -> RenderScaffold(node)
            is UiNode.Unknown -> RenderUnknown(node)
        }
    }

    @Composable
    private fun RenderColumn(node: UiNode.Column) {
        val verticalArrangement = when {
            node.verticalArrangement?.contains("spacedBy") == true -> {
                val space = extractNumber(node.verticalArrangement)
                Arrangement.spacedBy(space)
            }
            node.verticalArrangement?.contains("SpaceBetween") == true -> Arrangement.SpaceBetween
            node.verticalArrangement?.contains("SpaceEvenly") == true -> Arrangement.SpaceEvenly
            node.verticalArrangement?.contains("SpaceAround") == true -> Arrangement.SpaceAround
            node.verticalArrangement?.contains("Center") == true -> Arrangement.Center
            else -> Arrangement.Top
        }

        val horizontalAlignment = when {
            node.horizontalAlignment?.contains("CenterHorizontally") == true -> Alignment.CenterHorizontally
            node.horizontalAlignment?.contains("End") == true -> Alignment.End
            node.horizontalAlignment?.contains("Start") == true -> Alignment.Start
            else -> Alignment.Start
        }

        Column(
            modifier = buildModifier(node.modifier),
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment
        ) {
            node.children.forEach { RenderNode(it) }
        }
    }

    @Composable
    private fun RenderRow(node: UiNode.Row) {
        val horizontalArrangement = when {
            node.horizontalArrangement?.contains("spacedBy") == true -> {
                val space = extractNumber(node.horizontalArrangement)
                Arrangement.spacedBy(space)
            }
            node.horizontalArrangement?.contains("SpaceBetween") == true -> Arrangement.SpaceBetween
            node.horizontalArrangement?.contains("SpaceEvenly") == true -> Arrangement.SpaceEvenly
            node.horizontalArrangement?.contains("SpaceAround") == true -> Arrangement.SpaceAround
            node.horizontalArrangement?.contains("Center") == true -> Arrangement.Center
            node.horizontalArrangement?.contains("End") == true -> Arrangement.End
            else -> Arrangement.Start
        }

        val verticalAlignment = when {
            node.verticalAlignment?.contains("CenterVertically") == true -> Alignment.CenterVertically
            node.verticalAlignment?.contains("Top") == true -> Alignment.Top
            node.verticalAlignment?.contains("Bottom") == true -> Alignment.Bottom
            else -> Alignment.Top
        }

        Row(
            modifier = buildModifier(node.modifier),
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = verticalAlignment
        ) {
            node.children.forEach { RenderNode(it) }
        }
    }

    @Composable
    private fun RenderBox(node: UiNode.Box) {
        val contentAlignment = when {
            node.contentAlignment?.contains("Center") == true -> Alignment.Center
            node.contentAlignment?.contains("TopStart") == true -> Alignment.TopStart
            node.contentAlignment?.contains("TopEnd") == true -> Alignment.TopEnd
            node.contentAlignment?.contains("BottomStart") == true -> Alignment.BottomStart
            node.contentAlignment?.contains("BottomEnd") == true -> Alignment.BottomEnd
            node.contentAlignment?.contains("TopCenter") == true -> Alignment.TopCenter
            node.contentAlignment?.contains("BottomCenter") == true -> Alignment.BottomCenter
            else -> Alignment.TopStart
        }

        Box(
            modifier = buildModifier(node.modifier),
            contentAlignment = contentAlignment
        ) {
            node.children.forEach { RenderNode(it) }
        }
    }

    @Composable
    private fun RenderText(node: UiNode.Text) {
        val color = parseColor(node.color)
        val fontSize = node.fontSize?.let { parseNumberToSp(it) } ?: MaterialTheme.typography.bodyMedium.fontSize
        val fontWeight = when (node.fontWeight) {
            "Bold" -> FontWeight.Bold
            "SemiBold" -> FontWeight.SemiBold
            "Medium" -> FontWeight.Medium
            "Light" -> FontWeight.Light
            "Normal" -> FontWeight.Normal
            else -> FontWeight.Normal
        }
        val textAlign = when (node.textAlign) {
            "Center" -> TextAlign.Center
            "End" -> TextAlign.End
            "Right" -> TextAlign.Right
            "Start" -> TextAlign.Start
            "Left" -> TextAlign.Left
            else -> null
        }

        Text(
            text = node.text.ifEmpty { "(Text)" },
            modifier = buildModifier(node.modifier),
            color = color ?: MaterialTheme.colorScheme.onSurface,
            fontSize = fontSize,
            fontWeight = fontWeight,
            maxLines = node.maxLines ?: Int.MAX_VALUE,
            textAlign = textAlign
        )
    }

    @Composable
    private fun RenderButton(node: UiNode.Button) {
        Button(
            onClick = { /* Preview mode - click not wired */ },
            modifier = buildModifier(node.modifier),
            enabled = node.enabled
        ) {
            Text(node.text.ifEmpty { "Button" })
        }
    }

    @Composable
    private fun RenderOutlinedButton(node: UiNode.OutlinedButton) {
        OutlinedButton(
            onClick = { },
            modifier = buildModifier(node.modifier)
        ) {
            Text(node.text.ifEmpty { "Outlined" })
        }
    }

    @Composable
    private fun RenderTextButton(node: UiNode.TextButton) {
        TextButton(
            onClick = { },
            modifier = buildModifier(node.modifier)
        ) {
            Text(node.text.ifEmpty { "TextBtn" })
        }
    }

    @Composable
    private fun RenderIcon(node: UiNode.Icon) {
        Box(
            modifier = buildModifier(node.modifier)
                .size(24.dp)
                .background(
                    parseColor(node.tint) ?: MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("✦", color = parseColor(node.tint) ?: MaterialTheme.colorScheme.primary)
        }
    }

    @Composable
    private fun RenderImage(node: UiNode.Image) {
        Box(
            modifier = buildModifier(node.modifier)
                .sizeIn(maxWidth = 200.dp, maxHeight = 200.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🖼️", fontSize = 24.sp)
                Text(
                    text = node.painterName ?: "(Image)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    @Composable
    private fun RenderSpacer(node: UiNode.Spacer) {
        Spacer(modifier = buildModifier(node.modifier))
    }

    @Composable
    private fun RenderDivider(node: UiNode.Divider) {
        val color = parseColor(node.color) ?: MaterialTheme.colorScheme.outlineVariant
        val thickness = node.thickness?.let { parseNumberToDp(it) } ?: 1.dp
        Divider(
            modifier = buildModifier(node.modifier),
            thickness = thickness,
            color = color
        )
    }

    @Composable
    private fun RenderCircularProgress(node: UiNode.CircularProgressIndicator) {
        CircularProgressIndicator(
            modifier = buildModifier(node.modifier),
            color = parseColor(node.color) ?: MaterialTheme.colorScheme.primary,
            strokeWidth = node.strokeWidth?.let { parseNumberToDp(it) } ?: 4.dp
        )
    }

    @Composable
    private fun RenderLinearProgress(node: UiNode.LinearProgressIndicator) {
        LinearProgressIndicator(
            modifier = buildModifier(node.modifier),
            color = parseColor(node.color) ?: MaterialTheme.colorScheme.primary
        )
    }

    @Composable
    private fun RenderSurface(node: UiNode.Surface) {
        val bgColor = parseColor(node.color) ?: MaterialTheme.colorScheme.surface
        val shape = parseShape(node.shape)

        Surface(
            modifier = buildModifier(node.modifier),
            color = bgColor,
            shape = shape ?: RoundedCornerShape(0.dp)
        ) {
            node.children.forEach { RenderNode(it) }
        }
    }

    @Composable
    private fun RenderCard(node: UiNode.Card) {
        Card(
            modifier = buildModifier(node.modifier)
        ) {
            node.children.forEach { RenderNode(it) }
        }
    }

    @Composable
    private fun RenderLazyColumn(node: UiNode.LazyColumn) {
        if (node.items.isEmpty()) {
            Box(
                modifier = buildModifier(node.modifier)
                    .heightIn(min = 40.dp)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("(LazyColumn)", style = MaterialTheme.typography.labelSmall)
            }
        } else {
            Column(
                modifier = buildModifier(node.modifier)
            ) {
                node.items.forEach { RenderNode(it) }
            }
        }
    }

    @Composable
    private fun RenderLazyRow(node: UiNode.LazyRow) {
        if (node.items.isEmpty()) {
            Box(
                modifier = buildModifier(node.modifier)
                    .widthIn(min = 40.dp)
                    .height(40.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("(LazyRow)", style = MaterialTheme.typography.labelSmall)
            }
        } else {
            Row(
                modifier = buildModifier(node.modifier)
            ) {
                node.items.forEach { RenderNode(it) }
            }
        }
    }

    @Composable
    private fun RenderScaffold(node: UiNode.Scaffold) {
        Scaffold(
            modifier = buildModifier(node.modifier)
        ) {
            if (node.content != null) {
                RenderNode(node.content)
            }
        }
    }

    @Composable
    private fun RenderUnknown(node: UiNode.Unknown) {
        Box(
            modifier = buildModifier(node.modifier)
                .fillMaxWidth()
                .padding(4.dp)
                .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f))
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "<? ${node.name} ?>",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
                if (node.error != null) {
                    Text(
                        text = node.error,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // ── Modifier Builder ──

    @Composable
    private fun buildModifier(model: ModifierModel): Modifier {
        var modifier: Modifier = Modifier
        for (entry in model.entries) {
            modifier = when (entry) {
                is ModifierEntry.Size -> modifier.then(
                    Modifier.size(
                        width = entry.width?.let { parseNumberToDp(it) } ?: Dp.Unspecified,
                        height = entry.height?.let { parseNumberToDp(it) } ?: Dp.Unspecified
                    )
                )
                is ModifierEntry.Padding -> {
                    val all = entry.all?.let { parseNumberToDp(it) }
                    if (all != null) {
                        modifier.padding(all)
                    } else {
                        modifier.padding(
                            start = entry.start?.let { parseNumberToDp(it) } ?: 0.dp,
                            end = entry.end?.let { parseNumberToDp(it) } ?: 0.dp,
                            top = entry.top?.let { parseNumberToDp(it) } ?: 0.dp,
                            bottom = entry.bottom?.let { parseNumberToDp(it) } ?: 0.dp
                        )
                    }
                }
                is ModifierEntry.FillMaxWidth -> modifier.fillMaxWidth()
                is ModifierEntry.FillMaxHeight -> modifier.fillMaxHeight()
                is ModifierEntry.FillMaxSize -> modifier.fillMaxSize()
                is ModifierEntry.Weight -> modifier // weight only works inside Row/Column scope
                is ModifierEntry.Background -> {
                    val color = parseColor(entry.color) ?: MaterialTheme.colorScheme.surface
                    val shape = parseShape(entry.shape) ?: RoundedCornerShape(0.dp)
                    modifier.background(color, shape)
                }
                is ModifierEntry.Clip -> {
                    val shape = parseShape(entry.shape) ?: RoundedCornerShape(0.dp)
                    modifier.clip(shape)
                }
                is ModifierEntry.Border -> {
                    val width = entry.width?.let { parseNumberToDp(it) } ?: 1.dp
                    val color = parseColor(entry.color) ?: MaterialTheme.colorScheme.outline
                    val shape = parseShape(entry.shape) ?: RoundedCornerShape(0.dp)
                    modifier.border(width, color, shape)
                }
                is ModifierEntry.Clickable -> modifier.clickable(
                    enabled = entry.enabled
                ) { /* Preview mode */ }
                is ModifierEntry.Width -> modifier.width(
                    parseNumberToDp(entry.value)
                )
                is ModifierEntry.Height -> modifier.height(
                    parseNumberToDp(entry.value)
                )
                is ModifierEntry.DefaultMinSize -> modifier.defaultMinSize(
                    minWidth = entry.minWidth?.let { parseNumberToDp(it) } ?: 0.dp,
                    minHeight = entry.minHeight?.let { parseNumberToDp(it) } ?: 0.dp
                )
                is ModifierEntry.WidthIn -> modifier.widthIn(
                    min = entry.min?.let { parseNumberToDp(it) } ?: 0.dp,
                    max = entry.max?.let { parseNumberToDp(it) } ?: Dp.Unspecified
                )
                is ModifierEntry.HeightIn -> modifier.heightIn(
                    min = entry.min?.let { parseNumberToDp(it) } ?: 0.dp,
                    max = entry.max?.let { parseNumberToDp(it) } ?: Dp.Unspecified
                )
                is ModifierEntry.Offset -> modifier.offset(
                    x = entry.x?.let { parseNumberToDp(it) } ?: 0.dp,
                    y = entry.y?.let { parseNumberToDp(it) } ?: 0.dp
                )
                is ModifierEntry.Alpha -> modifier.alpha(entry.value.value)
                is ModifierEntry.ZIndex -> modifier // zIndex not available in this context
                is ModifierEntry.Rotate -> modifier.rotate(entry.degrees.value)
                is ModifierEntry.Scale -> modifier.scale(entry.scale.value)
                is ModifierEntry.Margin -> modifier.padding(entry.all?.let { parseNumberToDp(it) } ?: 0.dp)
                is ModifierEntry.UnknownModifier -> modifier // Skip unknown
            }
        }
        return modifier
    }

    // ── Helper functions ──

    private fun parseNumberToDp(number: NumberModel): Dp {
        return when (number.unit) {
            "dp" -> number.value.dp
            "sp" -> number.value.dp
            "px" -> number.value.dp
            "%" -> number.value.dp
            else -> number.value.dp
        }
    }

    private fun parseNumberToSp(number: NumberModel): androidx.compose.ui.unit.TextUnit {
        return when (number.unit) {
            "sp" -> number.value.sp
            "dp" -> number.value.sp
            "px" -> number.value.sp
            else -> number.value.sp
        }
    }

    @Composable private fun parseColor(colorStr: String?): Color? {
        if (colorStr == null) return null

        return when {
            // Named Compose colors
            colorStr == "Color.Red" || colorStr == "Red" -> Color.Red
            colorStr == "Color.Blue" || colorStr == "Blue" -> Color.Blue
            colorStr == "Color.Green" || colorStr == "Green" -> Color.Green
            colorStr == "Color.White" || colorStr == "White" -> Color.White
            colorStr == "Color.Black" || colorStr == "Black" -> Color.Black
            colorStr == "Color.Gray" || colorStr == "Gray" -> Color.Gray
            colorStr == "Color.Yellow" || colorStr == "Yellow" -> Color.Yellow
            colorStr == "Color.Magenta" || colorStr == "Magenta" -> Color.Magenta
            colorStr == "Color.Cyan" || colorStr == "Cyan" -> Color.Cyan
            colorStr == "Color.Transparent" || colorStr == "Transparent" -> Color.Transparent

            // Material colors
            colorStr.startsWith("MaterialTheme.colorScheme.") -> {
                mapOf(
                    "primary" to MaterialTheme.colorScheme.primary,
                    "onPrimary" to MaterialTheme.colorScheme.onPrimary,
                    "secondary" to MaterialTheme.colorScheme.secondary,
                    "tertiary" to MaterialTheme.colorScheme.tertiary,
                    "background" to MaterialTheme.colorScheme.background,
                    "surface" to MaterialTheme.colorScheme.surface,
                    "surfaceVariant" to MaterialTheme.colorScheme.surfaceVariant,
                    "error" to MaterialTheme.colorScheme.error,
                    "onBackground" to MaterialTheme.colorScheme.onBackground,
                    "onSurface" to MaterialTheme.colorScheme.onSurface,
                    "onSurfaceVariant" to MaterialTheme.colorScheme.onSurfaceVariant,
                    "outline" to MaterialTheme.colorScheme.outline
                )[colorStr.removePrefix("MaterialTheme.colorScheme.")]
            }

            // Hex colors: Color(0xFFRRGGBB) or Color(0xFFRRGGBBAA)
            colorStr.matches(Regex("""Color\(0x([0-9a-fA-F]{8})\)""")) -> {
                val hex = Regex("""Color\(0x([0-9a-fA-F]{8})\)""").find(colorStr)!!.groupValues[1]
                Color(hex.toLong(16) or 0x00000000FFFFFFFF)
            }

            // Raw hex
            colorStr.matches(Regex("""0x[0-9a-fA-F]{6,8}""")) -> {
                val hex = colorStr.removePrefix("0x")
                Color(hex.toLong(16) or 0x00000000FFFFFFFF)
            }

            // Color primary, Color secondary (Material colors shortcut)
            colorStr.startsWith("Color.") -> {
                // Try to parse Color(argb)
                val argMatch = Regex("""Color\(([^)]+)\)""").find(colorStr)
                if (argMatch != null) {
                    // Color(red, green, blue) or Color(red, green, blue, alpha)
                    val parts = argMatch.groupValues[1].split(",").map { it.trim() }
                    if (parts.size == 3) {
                        try {
                            Color(
                                parts[0].toFloat() / 255f,
                                parts[1].toFloat() / 255f,
                                parts[2].toFloat() / 255f
                            )
                        } catch (e: Exception) { null }
                    } else null
                } else null
            }

            else -> null
        }
    }

    @Composable private fun parseShape(shapeStr: String?): Shape? {
        if (shapeStr == null) return null

        val circleMatch = Regex("""CircleShape""").find(shapeStr)
        if (circleMatch != null) return RoundedCornerShape(50)

        val roundedMatch = Regex("""RoundedCornerShape\((\d+(?:\.\d+)?)\s*(?:\.\w+)?\)""").find(shapeStr)
        if (roundedMatch != null) {
            val dp = roundedMatch.groupValues[1].toFloatOrNull() ?: return null
            return RoundedCornerShape(dp.dp)
        }

        return null
    }

    @Composable private fun extractNumber(expr: String): Dp {
        val match = Regex("""(\d+(?:\.\d+)?)\s*(?:\.\w+)?""").find(expr)
        return match?.let { it.groupValues[1].toFloatOrNull()?.dp } ?: 0.dp
    }
}
