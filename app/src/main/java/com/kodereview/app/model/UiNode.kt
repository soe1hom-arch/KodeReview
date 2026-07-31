package com.kodereview.app.model

/**
 * Represents a parsed Compose UI tree element.
 * Built by ComposePreviewParser and rendered by ComposePreviewRenderer.
 */
sealed class UiNode {
    abstract val modifier: ModifierModel

    // Layout containers
    data class Column(
        override val modifier: ModifierModel = ModifierModel(),
        val children: List<UiNode> = emptyList(),
        val verticalArrangement: String? = null,
        val horizontalAlignment: String? = null
    ) : UiNode()

    data class Row(
        override val modifier: ModifierModel = ModifierModel(),
        val children: List<UiNode> = emptyList(),
        val horizontalArrangement: String? = null,
        val verticalAlignment: String? = null
    ) : UiNode()

    data class Box(
        override val modifier: ModifierModel = ModifierModel(),
        val children: List<UiNode> = emptyList(),
        val contentAlignment: String? = null
    ) : UiNode()

    data class Surface(
        override val modifier: ModifierModel = ModifierModel(),
        val children: List<UiNode> = emptyList(),
        val color: String? = null,
        val shape: String? = null
    ) : UiNode()

    data class Card(
        override val modifier: ModifierModel = ModifierModel(),
        val children: List<UiNode> = emptyList()
    ) : UiNode()

    // Elements
    data class Text(
        override val modifier: ModifierModel = ModifierModel(),
        val text: String = "",
        val color: String? = null,
        val fontSize: NumberModel? = null,
        val fontWeight: String? = null,
        val maxLines: Int? = null,
        val textAlign: String? = null
    ) : UiNode()

    data class Button(
        override val modifier: ModifierModel = ModifierModel(),
        val text: String = "",
        val enabled: Boolean = true,
        val onClickAvailable: Boolean = false
    ) : UiNode()

    data class OutlinedButton(
        override val modifier: ModifierModel = ModifierModel(),
        val text: String = ""
    ) : UiNode()

    data class TextButton(
        override val modifier: ModifierModel = ModifierModel(),
        val text: String = ""
    ) : UiNode()

    data class IconButton(
        override val modifier: ModifierModel = ModifierModel(),
        val text: String = ""
    ) : UiNode()

    data class Icon(
        override val modifier: ModifierModel = ModifierModel(),
        val name: String? = null,
        val tint: String? = null
    ) : UiNode()

    data class Image(
        override val modifier: ModifierModel = ModifierModel(),
        val painterName: String? = null,
        val contentDescription: String? = null,
        val contentScale: String? = null
    ) : UiNode()

    data class Spacer(
        override val modifier: ModifierModel = ModifierModel()
    ) : UiNode()

    data class Divider(
        override val modifier: ModifierModel = ModifierModel(),
        val color: String? = null,
        val thickness: NumberModel? = null
    ) : UiNode()

    data class CircularProgressIndicator(
        override val modifier: ModifierModel = ModifierModel(),
        val color: String? = null,
        val strokeWidth: NumberModel? = null
    ) : UiNode()

    data class LinearProgressIndicator(
        override val modifier: ModifierModel = ModifierModel(),
        val color: String? = null
    ) : UiNode()

    // Lazy lists
    data class LazyColumn(
        override val modifier: ModifierModel = ModifierModel(),
        val items: List<UiNode> = emptyList(),
        val itemCount: Int? = null
    ) : UiNode()

    data class LazyRow(
        override val modifier: ModifierModel = ModifierModel(),
        val items: List<UiNode> = emptyList()
    ) : UiNode()

    // Scaffold / top-level
    data class Scaffold(
        override val modifier: ModifierModel = ModifierModel(),
        val content: UiNode? = null,
        val topBar: UiNode? = null,
        val bottomBar: UiNode? = null
    ) : UiNode()

    // Navigation components
    data class TopAppBar(
        override val modifier: ModifierModel = ModifierModel(),
        val title: String = ""
    ) : UiNode()

    data class NavigationBar(
        override val modifier: ModifierModel = ModifierModel(),
        val children: List<UiNode> = emptyList()
    ) : UiNode()

    data class NavigationBarItem(
        override val modifier: ModifierModel = ModifierModel(),
        val selected: Boolean = false,
        val label: String = ""
    ) : UiNode()

    // Input components
    data class Switch(
        override val modifier: ModifierModel = ModifierModel(),
        val checked: Boolean = false
    ) : UiNode()

    data class Checkbox(
        override val modifier: ModifierModel = ModifierModel(),
        val checked: Boolean = false
    ) : UiNode()

    // Dialog
    data class Dialog(
        override val modifier: ModifierModel = ModifierModel(),
        val title: String? = null,
        val text: String? = null,
        val children: List<UiNode> = emptyList()
    ) : UiNode()

    // Drawer
    data class ModalNavigationDrawer(
        override val modifier: ModifierModel = ModifierModel(),
        val content: UiNode? = null,
        val drawerContent: UiNode? = null
    ) : UiNode()

    data class ModalDrawerSheet(
        override val modifier: ModifierModel = ModifierModel(),
        val children: List<UiNode> = emptyList()
    ) : UiNode()

    // FAB
    data class FloatingActionButton(
        override val modifier: ModifierModel = ModifierModel(),
        val text: String = ""
    ) : UiNode()

    // Placeholder for unknown composables
    data class Unknown(
        override val modifier: ModifierModel = ModifierModel(),
        val name: String = "",
        val error: String? = null,
        val children: List<UiNode> = emptyList()
    ) : UiNode()
}
