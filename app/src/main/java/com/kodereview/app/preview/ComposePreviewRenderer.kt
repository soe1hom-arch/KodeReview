package com.kodereview.app.preview

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kodereview.app.model.*

/**
 * Renders a parsed UiNode tree into actual Jetpack Compose UI.
 * Uses custom progress indicators to avoid BOM version issues.
 */
@OptIn(ExperimentalMaterial3Api::class)
object ComposePreviewRenderer {

    /** Theme color registry (identifier -> Color expression) used by parseColor. */
    private var activeColors: Map<String, String> = emptyMap()

    /**
     * True while rendering children of a vertically scrollable container.
     * Used to suppress layout modifiers (fillMaxHeight/fillMaxSize/weight)
     * that would otherwise crash with an infinite height constraint.
     */
    private val LocalInScroll = staticCompositionLocalOf { false }

    @Composable
    fun Render(nodes: List<UiNode>, colors: Map<String, String> = emptyMap()): Unit {
        // Preview panel is single-instance, so setting the registry before
        // composition is safe; it is only read during this render pass.
        activeColors = colors
        // Full-screen layouts (Scaffold / drawer) get a bounded viewport so
        // topBar/content/bottomBar lay out like a real phone screen. Everything
        // else is rendered as a scrollable document so tall content is never
        // clipped and the user can always scroll to see it all.
        val fullScreen = nodes.any { it is UiNode.Scaffold || it is UiNode.ModalNavigationDrawer }
        if (fullScreen) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                nodes.forEach { node ->
                    RenderNode(node)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp)
            ) {
                CompositionLocalProvider(LocalInScroll provides true) {
                    nodes.forEach { node ->
                        RenderNode(node)
                    }
                }
            }
        }
    }

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
            is UiNode.IconButton -> RenderIconButton(node)
            is UiNode.Icon -> RenderIcon(node)
            is UiNode.Image -> RenderImage(node)
            is UiNode.Spacer -> RenderSpacer(node)
            is UiNode.Divider -> RenderDivider(node)
            is UiNode.CircularProgressIndicator -> RenderCustomCircularProgress(node)
            is UiNode.LinearProgressIndicator -> RenderCustomLinearProgress(node)
            is UiNode.Surface -> RenderSurface(node)
            is UiNode.Card -> RenderCard(node)
            is UiNode.LazyColumn -> RenderLazyColumn(node)
            is UiNode.LazyRow -> RenderLazyRow(node)
            is UiNode.Scaffold -> RenderScaffold(node)
            is UiNode.TopAppBar -> RenderTopAppBarView(node)
            is UiNode.NavigationBar -> RenderNavigationBarView(node)
            is UiNode.NavigationBarItem -> RenderNavigationBarItemView(node)
            is UiNode.Switch -> RenderSwitchCompat(node)
            is UiNode.Checkbox -> RenderCheckboxCompat(node)
            is UiNode.Dialog -> RenderDialogCompat(node)
            is UiNode.ModalNavigationDrawer -> RenderModalDrawerCompat(node)
            is UiNode.ModalDrawerSheet -> RenderModalDrawerSheetCompat(node)
            is UiNode.FloatingActionButton -> RenderFloatingActionButtonView(node)
            is UiNode.Unknown -> RenderUnknown(node)
            is UiNode.Noop -> Unit
        }
    }

    // ── Layout containers ──

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
            val inScroll = LocalInScroll.current
            node.children.forEach { child ->
                val weight = if (inScroll) null else child.modifier.entries
                    .filterIsInstance<ModifierEntry.Weight>()
                    .firstOrNull()?.weight?.value
                if (weight != null) {
                    Box(
                        modifier = Modifier
                            .weight(weight)
                            .fillMaxWidth()
                    ) {
                        RenderNode(child)
                    }
                } else {
                    RenderNode(child)
                }
            }
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
            val inScroll = LocalInScroll.current
            node.children.forEach { child ->
                val weight = if (inScroll) null else child.modifier.entries
                    .filterIsInstance<ModifierEntry.Weight>()
                    .firstOrNull()?.weight?.value
                if (weight != null) {
                    Box(
                        modifier = Modifier
                            .weight(weight)
                            .fillMaxHeight()
                    ) {
                        RenderNode(child)
                    }
                } else {
                    RenderNode(child)
                }
            }
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
            node.contentAlignment?.contains("CenterStart") == true -> Alignment.CenterStart
            node.contentAlignment?.contains("CenterEnd") == true -> Alignment.CenterEnd
            else -> Alignment.TopStart
        }
        Box(
            modifier = buildModifier(node.modifier),
            contentAlignment = contentAlignment
        ) {
            node.children.forEach { RenderNode(it) }
        }
    }

    // ── Elements ──

    @Composable
    private fun RenderText(node: UiNode.Text) {
        val color = parseColor(node.color) ?: MaterialTheme.colorScheme.onSurface
        val fontSize = node.fontSize?.let { parseNumberToSp(it) } ?: MaterialTheme.typography.bodyMedium.fontSize
        val fontWeight = when (node.fontWeight?.lowercase()) {
            "bold" -> FontWeight.Bold
            "normal" -> FontWeight.Normal
            "light" -> FontWeight.Light
            "medium" -> FontWeight.Medium
            "semibold" -> FontWeight.SemiBold
            else -> null
        }
        val textAlign = when {
            node.textAlign?.contains("Center") == true -> TextAlign.Center
            node.textAlign?.contains("End") == true -> TextAlign.End
            node.textAlign?.contains("Start") == true -> TextAlign.Start
            else -> TextAlign.Start
        }
        Text(
            text = node.text,
            modifier = buildModifier(node.modifier),
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            maxLines = node.maxLines ?: Int.MAX_VALUE,
            textAlign = textAlign
        )
    }

    @Composable
    private fun RenderButton(node: UiNode.Button) {
        Button(
            onClick = { },
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
            Text(node.text.ifEmpty { "Text" })
        }
    }

    @Composable
    private fun RenderIconButton(node: UiNode.IconButton) {
        IconButton(
            onClick = { },
            modifier = buildModifier(node.modifier)
        ) {
            Text(node.text.ifEmpty { "🔘" }, fontSize = 12.sp)
        }
    }

    @Composable
    private fun RenderIcon(node: UiNode.Icon) {
        val tint = parseColor(node.tint) ?: MaterialTheme.colorScheme.onSurface
        Box(
            modifier = buildModifier(node.modifier)
                .size(24.dp)
                .background(tint.copy(alpha = 0.1f), RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("◆", color = tint, fontSize = 14.sp)
        }
    }

    @Composable
    private fun RenderImage(node: UiNode.Image) {
        Box(
            modifier = buildModifier(node.modifier)
                .sizeIn(minWidth = 40.dp, minHeight = 40.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🖼", fontSize = 20.sp)
                node.contentDescription?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    @Composable
    private fun RenderSpacer(node: UiNode.Spacer) {
        Spacer(modifier = buildModifier(node.modifier))
    }

    @Composable
    private fun RenderDivider(node: UiNode.Divider) {
        val color = parseColor(node.color) ?: MaterialTheme.colorScheme.outline
        val thickness = node.thickness?.let { parseNumberToDp(it) } ?: 1.dp
        Divider(
            modifier = buildModifier(node.modifier),
            color = color,
            thickness = thickness
        )
    }

    // ── Custom Progress Indicators ──

    @Composable
    private fun RenderCustomCircularProgress(node: UiNode.CircularProgressIndicator) {
        val color = parseColor(node.color) ?: MaterialTheme.colorScheme.primary
        Box(
            modifier = buildModifier(node.modifier)
                .size(24.dp)
                .background(color.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("⟳", color = color, fontSize = 14.sp)
        }
    }

    @Composable
    private fun RenderCustomLinearProgress(node: UiNode.LinearProgressIndicator) {
        val color = parseColor(node.color) ?: MaterialTheme.colorScheme.primary
        Column(modifier = buildModifier(node.modifier).heightIn(min = 4.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color.copy(alpha = 0.2f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(color)
                )
            }
        }
    }

    // ── Surfaces & Cards ──

    @Composable
    private fun RenderSurface(node: UiNode.Surface) {
        val color = parseColor(node.color) ?: MaterialTheme.colorScheme.surface
        val shape = parseShape(node.shape) ?: RoundedCornerShape(0.dp)
        Surface(
            modifier = buildModifier(node.modifier),
            color = color,
            shape = shape
        ) {
            Column { node.children.forEach { RenderNode(it) } }
        }
    }

    @Composable
    private fun RenderCard(node: UiNode.Card) {
        Card(
            modifier = buildModifier(node.modifier),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                node.children.forEach { RenderNode(it) }
            }
        }
    }

    // ── Lazy Lists ──

    @Composable
    private fun RenderLazyColumn(node: UiNode.LazyColumn) {
        // Inside a scrollable document a nested scrollable is illegal
        // (infinite height constraints); the items are simply stacked.
        val inScroll = LocalInScroll.current
        val base = buildModifier(node.modifier)
        Column(
            modifier = if (inScroll) base
            else base.verticalScroll(androidx.compose.foundation.rememberScrollState())
        ) {
            CompositionLocalProvider(LocalInScroll provides true) {
                node.items.forEach { RenderNode(it) }
            }
        }
    }

    @Composable
    private fun RenderLazyRow(node: UiNode.LazyRow) {
        Row(
            modifier = buildModifier(node.modifier)
                .horizontalScroll(androidx.compose.foundation.rememberScrollState())
        ) {
            CompositionLocalProvider(LocalInScroll provides true) {
                node.items.forEach { RenderNode(it) }
            }
        }
    }

    // ── Scaffold ──

    @Composable
    private fun RenderScaffold(node: UiNode.Scaffold) {
        val inScroll = LocalInScroll.current
        // A real Scaffold needs a *bounded* height to lay out topBar/content/
        // bottomBar. Replicate that with a plain Column instead of material3
        // Scaffold, so the preview viewport behaves like a phone screen:
        // topBar on top, content filling the rest, bottomBar pinned at bottom.
        // When measured inside an unbounded (scrollable) context, fall back to
        // a simple vertical stack so nothing crashes and nothing is clipped.
        Column(
            modifier = buildModifier(node.modifier)
                .fillMaxWidth()
                .then(if (inScroll) Modifier else Modifier.fillMaxHeight())
        ) {
            node.topBar?.let { RenderNode(it) }
            if (inScroll) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    node.content?.let { RenderNode(it) }
                }
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    CompositionLocalProvider(LocalInScroll provides true) {
                        node.content?.let { RenderNode(it) }
                    }
                }
            }
            node.bottomBar?.let { RenderNode(it) }
        }
    }

    // ── Navigation Components ──

    @Composable
    private fun RenderTopAppBarView(node: UiNode.TopAppBar) {
        TopAppBar(
            title = {
                Text(
                    node.title.ifEmpty { "TopAppBar" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                if (node.navigationIcon != null) {
                    Text(
                        "☰",
                        fontSize = 22.sp,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                }
            },
            modifier = buildModifier(node.modifier)
        )
    }

    @Composable
    private fun RenderNavigationBarView(node: UiNode.NavigationBar) {
        NavigationBar(
            modifier = buildModifier(node.modifier)
        ) {
            node.children.forEach { child ->
                when (child) {
                    is UiNode.NavigationBarItem -> {
                        // NavigationBarItem is a RowScope extension function
                        // Inside NavigationBar lambda, RowScope receiver is available
                        NavigationBarItem(
                            selected = child.selected,
                            onClick = { },
                            icon = { Text(resourceGlyph(child.icon), fontSize = 16.sp) },
                            label = { Text(child.label.ifEmpty { "Item" }, fontSize = 10.sp, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                    else -> RenderNode(child)
                }
            }
        }
    }

    private fun resourceGlyph(resName: String?): String = when (resName) {
        "ic_home" -> "🏠"
        "ic_payload" -> "📦"
        "ic_super" -> "⚡"
        "ic_filesystem", "ic_file_manager", "ic_folder", "ic_files", "ic_file" -> "📁"
        "ic_boot_image", "ic_boot" -> "🛠"
        "ic_menu" -> "☰"
        "ic_info" -> "ℹ️"
        "ic_close" -> "✕"
        "ic_save_alt" -> "💾"
        "ic_delete" -> "🗑"
        "ic_cleaning_services" -> "🧹"
        "ic_content_copy" -> "📋"
        "ic_refresh" -> "🔄"
        "ic_chevron_right" -> "›"
        "ic_arrow_back" -> "‹"
        "ic_check_circle" -> "✅"
        "ic_error", "ic_warning" -> "⚠"
        else -> "◆"
    }

    @Composable
    private fun RenderNavigationBarItemView(node: UiNode.NavigationBarItem) {
        // NavigationBarItem requires RowScope - render as simple button
        OutlinedButton(
            onClick = { },
            modifier = buildModifier(node.modifier)
        ) {
            Text(
                node.label.ifEmpty { "Item" },
                fontSize = 10.sp
            )
        }
    }

    // ── Input Components ──

    @Composable
    private fun RenderSwitchCompat(node: UiNode.Switch) {
        Row(
            modifier = buildModifier(node.modifier),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .width(40.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(12.dp)),
                color = if (node.checked) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .offset(
                            x = if (node.checked) 18.dp else 2.dp,
                            y = 2.dp
                        )
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface)
                )
            }
        }
    }

    @Composable
    private fun RenderCheckboxCompat(node: UiNode.Checkbox) {
        Checkbox(
            checked = node.checked,
            onCheckedChange = { },
            modifier = buildModifier(node.modifier)
        )
    }

    // ── Dialog ──

    @Composable
    private fun RenderDialogCompat(node: UiNode.Dialog) {
        Surface(
            modifier = buildModifier(node.modifier)
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                node.title?.let {
                    Text(it, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(8.dp))
                }
                node.text?.let {
                    Text(it, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                }
                node.children.forEach { RenderNode(it) }
            }
        }
    }

    // ── Drawer ──

    @Composable
    private fun RenderModalDrawerCompat(node: UiNode.ModalNavigationDrawer) {
        Box(modifier = buildModifier(node.modifier).fillMaxWidth()) {
            // Main content (Scaffold with top bar + bottom navigation)
            node.content?.let { RenderNode(it) }
            if (node.content == null) {
                Text("(main content)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Scrim over the main content, like the real modal drawer
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.4f))
            )
            // Drawer sheet sliding in from the left with the app's own menu
            val sheetWidth = node.drawerContent?.modifier?.entries
                ?.filterIsInstance<ModifierEntry.Width>()
                ?.firstOrNull()?.value
                ?.let { parseNumberToDp(it) } ?: 300.dp
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(sheetWidth)
                    .fillMaxHeight()
            ) {
                node.drawerContent?.let { RenderNode(it) }
                if (node.drawerContent == null) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Menu",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        if (node.menuLabels.isNotEmpty()) {
                            node.menuLabels.forEach { label ->
                                Text(
                                    label,
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        } else {
                            Text(
                                "Navigation Drawer (menu)",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun RenderModalDrawerSheetCompat(node: UiNode.ModalDrawerSheet) {
        Surface(
            modifier = buildModifier(node.modifier).fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                node.children.forEach { RenderNode(it) }
            }
        }
    }

    // ── FAB ──

    @Composable
    private fun RenderFloatingActionButtonView(node: UiNode.FloatingActionButton) {
        FloatingActionButton(
            onClick = { },
            modifier = buildModifier(node.modifier)
        ) {
            Text(node.text.ifEmpty { "+" }, fontSize = 16.sp)
        }
    }

    // ── Unknown ──

    @Composable
    private fun RenderUnknown(node: UiNode.Unknown) {
        // Inlined custom composable — render its real children.
        if (node.children.isNotEmpty()) {
            node.children.forEach { RenderNode(it) }
            return
        }
        // Labelled custom component (e.g. drawer menu item) — show the label.
        if (!node.text.isNullOrBlank()) {
            Row(
                modifier = buildModifier(node.modifier)
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("·", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    node.text!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            return
        }
        // Component not found (usually defined in another file) — show a hint.
        Surface(
            modifier = buildModifier(node.modifier).padding(vertical = 2.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            shape = RoundedCornerShape(6.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
                Text(
                    text = "<${node.name} />",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Komponen dari file lain — buka folder proyek atau tambahkan file referensi",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }

    // ── Modifier builder ──

    @Composable
    private fun buildModifier(modifier: ModifierModel): Modifier {
        var m: Modifier = Modifier
        for (entry in modifier.entries) {
            m = when (entry) {
                is ModifierEntry.FillMaxWidth -> m.fillMaxWidth()
                is ModifierEntry.FillMaxHeight ->
                    if (LocalInScroll.current) m else m.fillMaxHeight()
                is ModifierEntry.FillMaxSize ->
                    if (LocalInScroll.current) m.fillMaxWidth() else m.fillMaxSize()
                is ModifierEntry.Width -> m.width(parseNumberToDp(entry.value))
                is ModifierEntry.Height -> m.height(parseNumberToDp(entry.value))
                is ModifierEntry.Size -> {
                    var sm = m
                    entry.width?.let { sm = sm.width(parseNumberToDp(it)) }
                    entry.height?.let { sm = sm.height(parseNumberToDp(it)) }
                    sm
                }
                is ModifierEntry.Padding -> {
                    var pm = m
                    entry.all?.let { pm = pm.padding(parseNumberToDp(it)) }
                    entry.horizontal?.let { pm = pm.padding(horizontal = parseNumberToDp(it)) }
                    entry.vertical?.let { pm = pm.padding(vertical = parseNumberToDp(it)) }
                    entry.start?.let { pm = pm.padding(start = parseNumberToDp(it)) }
                    entry.end?.let { pm = pm.padding(end = parseNumberToDp(it)) }
                    entry.top?.let { pm = pm.padding(top = parseNumberToDp(it)) }
                    entry.bottom?.let { pm = pm.padding(bottom = parseNumberToDp(it)) }
                    pm
                }
                is ModifierEntry.Weight -> m // weight is scope function, ignored
                is ModifierEntry.Background -> {
                    val bgColor = parseColor(entry.color) ?: MaterialTheme.colorScheme.surfaceVariant
                    m.background(bgColor)
                }
                is ModifierEntry.Clip -> {
                    val shape = parseShape(entry.shape) ?: RoundedCornerShape(0.dp)
                    m.clip(shape)
                }
                is ModifierEntry.Alpha -> m.alpha(entry.value?.value ?: 1f)
                is ModifierEntry.Clickable -> m.clickable(enabled = entry.enabled, onClick = { })
                is ModifierEntry.Border -> {
                    val width = entry.width?.let { parseNumberToDp(it) } ?: 1.dp
                    val color = parseColor(entry.color) ?: MaterialTheme.colorScheme.outline
                    val shape = parseShape(entry.shape) ?: RoundedCornerShape(0.dp)
                    m.border(width, color, shape)
                }
                is ModifierEntry.Offset -> {
                    val x = entry.x?.let { parseNumberToDp(it) } ?: 0.dp
                    val y = entry.y?.let { parseNumberToDp(it) } ?: 0.dp
                    m.offset(x, y)
                }
                is ModifierEntry.Rotate -> m.rotate(entry.degrees?.value ?: 0f)
                is ModifierEntry.Scale -> m.scale(entry.scale?.value ?: 1f)
                is ModifierEntry.UnknownModifier -> m
                else -> m
            }
        }
        return m
    }

    // ── Helpers ──

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
        val trimmed = colorStr.trim()
        // 1) Direct lookup in the theme color registry (e.g. "TerminalBackground").
        if (trimmed.isNotEmpty()) {
            activeColors[trimmed]?.let { return parseColor(it) }
        }
        // 2) Expressions like `if (a) Green else Red` — resolve each identifier
        //    against the registry and use the first hit.
        if (trimmed.contains(" if ") || trimmed.contains(" else ")) {
            val identifiers = Regex("""\b[A-Za-z_]\w*\b""").findAll(trimmed).map { it.value }
            for (id in identifiers) {
                activeColors[id]?.let { return parseColor(it) }
            }
        }
        return when {
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
            colorStr.matches(Regex("""Color\(0x([0-9a-fA-F]{8})\)""")) -> {
                val hex = Regex("""Color\(0x([0-9a-fA-F]{8})\)""").find(colorStr)!!.groupValues[1]
                Color(hex.toLong(16) or 0x00000000FFFFFFFF)
            }
            colorStr.matches(Regex("""0x[0-9a-fA-F]{6,8}""")) -> {
                val hex = colorStr.removePrefix("0x")
                Color(hex.toLong(16) or 0x00000000FFFFFFFF)
            }
            colorStr.startsWith("Color.") -> {
                val argMatch = Regex("""Color\(([^)]+)\)""").find(colorStr)
                if (argMatch != null) {
                    val parts = argMatch.groupValues[1].split(",").map { it.trim() }
                    if (parts.size == 3) {
                        try { Color(parts[0].toFloat() / 255f, parts[1].toFloat() / 255f, parts[2].toFloat() / 255f)
                        } catch (e: Exception) { null }
                    } else null
                } else null
            }
            else -> null
        }
    }

    @Composable private fun parseShape(shapeStr: String?): Shape? {
        if (shapeStr == null) return null
        if ("CircleShape" in shapeStr) return RoundedCornerShape(50)
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
