package com.kodereview.app.model

/**
 * Parsed modifier chain from a Compose component.
 */
data class ModifierModel(
    val entries: List<ModifierEntry> = emptyList()
)

sealed class ModifierEntry {
    data class Size(
        val width: NumberModel? = null,
        val height: NumberModel? = null
    ) : ModifierEntry()

    data class Padding(
        val all: NumberModel? = null,
        val start: NumberModel? = null,
        val end: NumberModel? = null,
        val top: NumberModel? = null,
        val bottom: NumberModel? = null,
        val horizontal: NumberModel? = null,
        val vertical: NumberModel? = null
    ) : ModifierEntry()

    object FillMaxWidth : ModifierEntry()
    object FillMaxHeight : ModifierEntry()
    object FillMaxSize : ModifierEntry()

    data class Weight(val weight: NumberModel?) : ModifierEntry()

    data class Background(
        val color: String? = null,
        val shape: String? = null
    ) : ModifierEntry()

    data class Clip(val shape: String? = null) : ModifierEntry()

    data class Border(
        val width: NumberModel? = null,
        val color: String? = null,
        val shape: String? = null
    ) : ModifierEntry()

    data class Clickable(val enabled: Boolean = true) : ModifierEntry()

    // Additional modifiers we recognize
    data class Width(val value: NumberModel) : ModifierEntry()
    data class Height(val value: NumberModel) : ModifierEntry()
    data class DefaultMinSize(
        val minWidth: NumberModel? = null,
        val minHeight: NumberModel? = null
    ) : ModifierEntry()
    data class WidthIn(val min: NumberModel? = null, val max: NumberModel? = null) : ModifierEntry()
    data class HeightIn(val min: NumberModel? = null, val max: NumberModel? = null) : ModifierEntry()
    data class Offset(val x: NumberModel? = null, val y: NumberModel? = null) : ModifierEntry()
    data class Margin(val all: NumberModel? = null) : ModifierEntry()
    data class Alpha(val value: NumberModel) : ModifierEntry()
    data class ZIndex(val value: NumberModel) : ModifierEntry()
    data class Rotate(val degrees: NumberModel) : ModifierEntry()
    data class Scale(val scale: NumberModel) : ModifierEntry()

    // Placeholder for unhandled modifiers
    data class UnknownModifier(val code: String) : ModifierEntry()
}

/**
 * Represents a numeric value with optional unit (dp, sp, px, em, %)
 */
data class NumberModel(
    val value: Float,
    val unit: String = "dp"  // dp, sp, px, em, %, or raw: "f"
) {
    companion object {
        fun parse(text: String): NumberModel? {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return null

            // Try matching patterns: 16.dp, 12.sp, 1.5f, 42, .5f
            val patterns = listOf(
                Regex("""^(\d+(?:\.\d+)?)\.(\w+)$""") to { m: MatchResult ->
                    NumberModel(m.groupValues[1].toFloat(), m.groupValues[2])
                },
                Regex("""^(\d+(?:\.\d+)?)f$""") to { m: MatchResult ->
                    NumberModel(m.groupValues[1].toFloat(), "px")
                },
                Regex("""^\.(\d+)f$""") to { m: MatchResult ->
                    NumberModel("0.${m.groupValues[1]}".toFloat(), "px")
                },
                Regex("""^(\d+)$""") to { m: MatchResult ->
                    NumberModel(m.groupValues[1].toFloat(), "px")
                },
                Regex("""^(\d+(?:\.\d+)?)$""") to { m: MatchResult ->
                    NumberModel(m.groupValues[1].toFloat(), "px")
                }
            )

            for ((pattern, mapper) in patterns) {
                val match = pattern.find(trimmed)
                if (match != null) return mapper(match)
            }

            return null
        }
    }
}
