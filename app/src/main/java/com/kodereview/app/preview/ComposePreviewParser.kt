package com.kodereview.app.preview

import com.kodereview.app.model.*
import kotlin.math.min

/**
 * Recursive descent parser that extracts a Compose UI tree from Kotlin source code.
 *
 * Handles a practical subset of Compose components for live preview:
 * Column, Row, Box, Text, Button, OutlinedButton, TextButton,
 * Image, Icon, Spacer, Divider, Surface, Card,
 * CircularProgressIndicator, LinearProgressIndicator,
 * LazyColumn, LazyRow, Scaffold
 */
class ComposePreviewParser(private val source: String) {

    private var pos = 0
    private var previewBlockStart = -1
    private var previewBlockEnd = -1

    /**
     * Parse all composable functions and return their UI trees.
     */
    fun parseAll(): List<ParsedComposable> {
        val composables = mutableListOf<ParsedComposable>()
        var searchPos = 0

        while (true) {
            val composable = parseNextComposable(searchPos) ?: break
            composables.add(composable)
            searchPos = composable.bodyEnd
        }

        return composables
    }

    /**
     * Parse the first or default composable (for preview).
     * Prefers @Preview annotated, then falls back to first @Composable.
     */
    fun parseForPreview(): ParsedComposable? {
        val all = parseAll()
        if (all.isEmpty()) return null

        // Prefer @Preview annotated
        val preview = all.find { it.hasPreviewAnnotation }
        return preview ?: all.last()
    }

    /**
     * Parse the composable at/after startPos. Returns null if no more found.
     */
    private fun parseNextComposable(startPos: Int): ParsedComposable? {
        var searchPos = startPos
        val sourceLen = source.length

        while (searchPos < sourceLen) {
            // Look for @Composable or @Preview annotation
            val atPos = source.indexOf('@', searchPos)
            if (atPos == -1 || atPos >= sourceLen) return null

            val annotationLine = extractLine(atPos)
            val hasComposable = annotationLine.contains("Composable")
            val hasPreview = annotationLine.contains("Preview")

            if (!hasComposable && !hasPreview) {
                searchPos = atPos + 1
                continue
            }

            // Find the 'fun' keyword after the annotation
            val funPos = source.indexOf("fun", atPos)
            if (funPos == -1) return null

            // Extract function name
            val afterFun = skipWhitespace(source, funPos + 3)
            val nameEnd = findIdentifierEnd(source, afterFun)
            val name = source.substring(afterFun, nameEnd)

            // Find opening parenthesis for parameters
            val paramStart = source.indexOf('(', afterFun)
            if (paramStart == -1) return null

            // Find matching closing paren
            val paramEnd = findMatchingParen(source, paramStart)
            if (paramEnd == -1) return null

            // Parse parameters
            val params = parseParameters(paramStart, paramEnd)

            // Find body - skip potential return type (: ReturnType)
            var bodySearchStart = paramEnd + 1
            val afterParen = skipWhitespace(source, bodySearchStart)
            var bodyStart = afterParen

            // Check for return type ": Type"
            if (bodyStart < sourceLen && source[bodyStart] == ':') {
                val afterColon = skipWhitespace(source, bodyStart + 1)
                val typeEnd = findExpressionEnd(source, afterColon)
                bodyStart = afterColon
                bodyStart = skipWhitespace(source, typeEnd + 1)
            }

            // Find opening brace for body
            if (bodyStart >= sourceLen || source[bodyStart] != '=') {
                // Normal body with braces
                val bracePos = skipToChar(bodyStart, '{')
                if (bracePos == -1) return null

                bodyStart = bracePos
                val bodyEnd = findMatchingBrace(source, bodyStart)
                if (bodyEnd == -1) return null

                // Parse the UI tree from the body
                val bodyContent = source.substring(bodyStart + 1, bodyEnd)
                val nodes = parseBody(bodyContent, bodyStart + 1)

                val composable = ParsedComposable(
                    name = name,
                    hasPreviewAnnotation = hasPreview,
                    parameters = params,
                    body = nodes,
                    bodyStart = bodyStart,
                    bodyEnd = bodyEnd + 1
                )

                return composable
            } else {
                // Expression body: fun name() = expression
                val eqPos = bodyStart
                val exprEnd = findExpressionEnd(source, eqPos + 1)
                val bodyContent = source.substring(eqPos + 1, exprEnd)
                val nodes = parseBody(bodyContent, eqPos + 1)

                val composable = ParsedComposable(
                    name = name,
                    hasPreviewAnnotation = hasPreview,
                    parameters = params,
                    body = nodes,
                    bodyStart = eqPos,
                    bodyEnd = exprEnd
                )

                return composable
            }
        }

        return null
    }

    /**
     * Parse a body block into a list of UiNodes.
     */
    private fun parseBody(content: String, offset: Int): List<UiNode> {
        val nodes = mutableListOf<UiNode>()
        var p = 0

        while (p < content.length) {
            p = skipWhitespaceAndComments(content, p)
            if (p >= content.length) break

            val ch = content[p]

            if (ch == '}') break  // End of current block

            // Variable/state declarations: val x = ..., var x by remember { ... }
            if ((ch == 'v' && content.regionMatches(p, "val ", 0, 4)) ||
                (ch == 'v' && content.regionMatches(p, "var ", 0, 4)) ||
                (ch == 'c' && content.regionMatches(p, "const ", 0, 6))
            ) {
                p = skipToChar(content, p, ';', '\n', '}', '(', ')') ?: (p + 1)
                continue
            }

            // Try to parse a composable call
            val node = tryParseCall(content, p, offset)
            if (node != null) {
                nodes.add(node.first)
                p = node.second
            } else {
                // Skip line
                val newLine = content.indexOf('\n', p)
                p = if (newLine == -1) content.length else newLine + 1
            }
        }

        return nodes
    }

    /**
     * Try to parse a composable call (Column, Text, etc.) at position p.
     * Returns the node and new position, or null if not a composable call.
     */
    private fun tryParseCall(content: String, p: Int, offset: Int): Pair<UiNode, Int>? {
        var pos = p
        pos = skipWhitespaceAndComments(content, pos)

        // Check if we have a named reference (start of a composable call)
        val nameStart = pos
        val name = parseIdentifier(content, pos) ?: return null
        pos += name.length

        pos = skipWhitespaceAndComments(content, pos)

        // It could be: Name(...), Name { }, or Name(...) { }
        if (pos >= content.length) return null

        val ch = content[pos]
        if (ch != '(' && ch != '{') return null

        // Parse arguments: Name(args...) or Name { content }
        val namedArgs = mutableMapOf<String, String>()
        var hasTrailingLambda = false
        var contentBody: String? = null

        // Check for a chain modifier before arguments
        // e.g., Modifier.padding(16.dp).fillMaxWidth()
        // This is handled inside parseNamedArgumentValue / parseModifierChain

        if (ch == '(') {
            // Parse arguments
            val closeParen = findMatchingParen(content, pos)
            if (closeParen == -1) return null

            val argsStr = content.substring(pos + 1, closeParen)
            val args = parseArguments(argsStr)
            namedArgs.putAll(args)
            hasTrailingLambda = false  // Lambda arguments are inside the parens

            // Check if there's a contents inside named args
            // e.g., Button(onClick = { ... })
            // The lambda value will be in the args map as onClick = "{ ... }"

            pos = closeParen + 1
            pos = skipWhitespaceAndComments(content, pos)
        }

        // Check for trailing lambda (the content block)
        if (pos < content.length && content[pos] == '{') {
            val bodyStart = pos
            val bodyEnd = findMatchingBrace(content, pos)
            if (bodyEnd != -1) {
                contentBody = content.substring(bodyStart + 1, bodyEnd)
                pos = bodyEnd + 1
                hasTrailingLambda = true
            }
        }

        // Build the UiNode
        val node = buildUiNode(name, namedArgs, contentBody, offset)
        return Pair(node, pos)
    }

    /**
     * Parse the arguments string (inside parentheses) into key-value pairs.
     */
    private fun parseArguments(argsStr: String): Map<String, String> {
        val args = mutableMapOf<String, String>()
        var p = 0

        while (p < argsStr.length) {
            p = skipWhitespaceAndComments(argsStr, p)
            if (p >= argsStr.length) break

            // Check for lambda: { ... }
            if (argsStr[p] == '{') {
                val end = findMatchingBrace(argsStr, p)
                if (end != -1) {
                    args["__lambda_${p}"] = argsStr.substring(p, end + 1)
                    p = end + 1
                    p = skipToCommaOrEnd(argsStr, p)
                    continue
                }
            }

            // Parse: name = value
            val identifierEnd = findIdentifierEnd(argsStr, p)
            if (identifierEnd <= p) {
                // Not an identifier, skip to comma
                p = skipToCommaOrEnd(argsStr, p)
                continue
            }

            val argName = argsStr.substring(p, identifierEnd)
            p = skipWhitespaceAndComments(argsStr, identifierEnd)

            if (p < argsStr.length && argsStr[p] == '=') {
                p++ // skip =
                p = skipWhitespaceAndComments(argsStr, p)
                // Parse value - find its end
                val (value, valueEnd) = parseArgumentValue(argsStr, p)
                args[argName] = value
                p = valueEnd
            } else {
                // Positional argument (no name) - skip
                val (_, valueEnd) = parseArgumentValue(argsStr, p)
                p = valueEnd
            }

            p = skipToCommaOrEnd(argsStr, p)
        }

        return args
    }

    /**
     * Parse a single argument value and return it with the end position.
     */
    private fun parseArgumentValue(content: String, start: Int): Pair<String, Int> {
        var p = start
        p = skipWhitespaceAndComments(content, p)
        if (p >= content.length) return Pair("", p)

        val ch = content[p]

        return when {
            ch == '"' -> {
                val strEnd = findStringEnd(content, p)
                val value = if (strEnd > p) content.substring(p, strEnd) else ""
                Pair(value, strEnd)
            }
            ch == '\'' -> {
                val end = content.indexOf('\'', p + 1)
                if (end != -1) Pair(content.substring(p, end + 1), end + 1)
                else Pair(content.substring(p), content.length)
            }
            ch == '{' -> {
                val end = findMatchingBrace(content, p)
                if (end != -1) Pair(content.substring(p, end + 1), end + 1)
                else Pair("", content.length)
            }
            ch == '(' -> {
                val end = findMatchingParen(content, p)
                if (end != -1) Pair(content.substring(p, end + 1), end + 1)
                else Pair("", content.length)
            }
            ch == ')' || ch == ',' || ch == '}' -> Pair("", p)
            else -> {
                // Read until: , ) } or whitespace
                val end = findValueEnd(content, p)
                Pair(content.substring(p, end), end)
            }
        }
    }

    /**
     * Build a UiNode from a parsed composable call.
     */
    private fun buildUiNode(
        name: String,
        args: Map<String, String>,
        contentBody: String?,
        offset: Int
    ): UiNode {
        val modifier = parseModifier(args["modifier"] ?: "")
        val text = extractStringArg(args, "text")
        val children = if (contentBody != null) parseBody(contentBody, offset) else emptyList()

        return when (name) {
            "Column" -> UiNode.Column(
                modifier = modifier,
                children = children,
                verticalArrangement = args["verticalArrangement"],
                horizontalAlignment = args["horizontalAlignment"]
            )
            "Row" -> UiNode.Row(
                modifier = modifier,
                children = children,
                horizontalArrangement = args["horizontalArrangement"],
                verticalAlignment = args["verticalAlignment"]
            )
            "Box" -> UiNode.Box(
                modifier = modifier,
                children = children,
                contentAlignment = args["contentAlignment"]
            )
            "Surface" -> UiNode.Surface(
                modifier = modifier,
                children = children,
                color = args["color"],
                shape = args["shape"]
            )
            "Card" -> UiNode.Card(
                modifier = modifier,
                children = children
            )
            "Text" -> UiNode.Text(
                modifier = modifier,
                text = text ?: "",
                color = args["color"],
                fontSize = parseNumber(args["fontSize"]),
                fontWeight = args["fontWeight"],
                maxLines = args["maxLines"]?.toIntOrNull(),
                textAlign = args["textAlign"]
            )
            "Button" -> UiNode.Button(
                modifier = modifier,
                text = text ?: "",
                onClickAvailable = args.containsKey("onClick"),
                enabled = args["enabled"]?.let { it != "false" } ?: true
            )
            "OutlinedButton" -> UiNode.OutlinedButton(
                modifier = modifier,
                text = text ?: ""
            )
            "TextButton" -> UiNode.TextButton(
                modifier = modifier,
                text = text ?: ""
            )
            "Icon" -> UiNode.Icon(
                modifier = modifier,
                name = args["imageVector"] ?: args["painter"] ?: args["bitmap"],
                tint = args["tint"]
            )
            "Image" -> UiNode.Image(
                modifier = modifier,
                painterName = args["painter"] ?: args["bitmap"],
                contentDescription = extractStringArg(args, "contentDescription") ?: "",
                contentScale = args["contentScale"]
            )
            "Spacer" -> UiNode.Spacer(modifier = modifier)
            "Divider" -> UiNode.Divider(
                modifier = modifier,
                color = args["color"],
                thickness = parseNumber(args["thickness"])
            )
            "CircularProgressIndicator" -> UiNode.CircularProgressIndicator(
                modifier = modifier,
                color = args["color"],
                strokeWidth = parseNumber(args["strokeWidth"])
            )
            "LinearProgressIndicator" -> UiNode.LinearProgressIndicator(
                modifier = modifier,
                color = args["color"]
            )
            "LazyColumn" -> UiNode.LazyColumn(
                modifier = modifier,
                items = children,
                itemCount = args["itemCount"]?.toIntOrNull()
            )
            "LazyRow" -> UiNode.LazyRow(
                modifier = modifier,
                items = children
            )
            "Scaffold" -> UiNode.Scaffold(
                modifier = modifier,
                content = children.firstOrNull()
            )
            else -> UiNode.Unknown(
                modifier = modifier,
                name = name,
                error = if (children.isNotEmpty() || text != null) null else "Unknown component"
            )
        }
    }

    /**
     * Parse a modifier chain expression like:
     * Modifier.fillMaxWidth().padding(16.dp).background(Color.Red)
     */
    private fun parseModifier(modifierStr: String): ModifierModel {
        val entries = mutableListOf<ModifierEntry>()
        if (modifierStr.isBlank()) return ModifierModel(entries)

        var p = 0
        // Skip "Modifier" prefix if present
        if (modifierStr.startsWith("Modifier")) {
            p = 8  // length of "Modifier"
        }

        while (p < modifierStr.length) {
            p = skipWhitespaceAndComments(modifierStr, p)
            if (p >= modifierStr.length || modifierStr[p] != '.') break

            p++ // skip '.'

            // Read modifier name
            val nameStart = p
            val nameEnd = findIdentifierEnd(modifierStr, p)
            if (nameEnd <= nameStart) break
            val modName = modifierStr.substring(nameStart, nameEnd)
            p = nameEnd

            // Parse arguments: (args...)
            p = skipWhitespaceAndComments(modifierStr, p)
            if (p >= modifierStr.length || modifierStr[p] != '(') {
                // No arguments - might be parameterless modifier
                when (modName) {
                    "fillMaxWidth" -> entries.add(ModifierEntry.FillMaxWidth)
                    "fillMaxHeight" -> entries.add(ModifierEntry.FillMaxHeight)
                    "fillMaxSize" -> entries.add(ModifierEntry.FillMaxSize)
                    else -> entries.add(ModifierEntry.UnknownModifier(modName))
                }
                continue
            }

            val closeParen = findMatchingParen(modifierStr, p)
            val argsStr = if (closeParen > p) modifierStr.substring(p + 1, closeParen) else ""
            p = if (closeParen > p) closeParen + 1 else p + 1

            val args = parseArguments(argsStr)

            // Map modifier name + args to ModifierEntry
            val entry = when (modName) {
                "size" -> ModifierEntry.Size(
                    width = parseNumber(args["width"] ?: args["__pos0"]),
                    height = parseNumber(args["height"] ?: args["__pos1"])
                )
                "width" -> ModifierEntry.Width(
                    value = parseNumber(args["__pos0"]) ?: NumberModel(0f)
                )
                "height" -> ModifierEntry.Height(
                    value = parseNumber(args["__pos0"]) ?: NumberModel(0f)
                )
                "padding" -> ModifierEntry.Padding(
                    all = parseNumber(args["all"] ?: args["__pos0"]),
                    start = parseNumber(args["start"]),
                    end = parseNumber(args["end"]),
                    top = parseNumber(args["top"]),
                    bottom = parseNumber(args["bottom"]),
                    horizontal = parseNumber(args["horizontal"]),
                    vertical = parseNumber(args["vertical"])
                )
                "fillMaxWidth" -> ModifierEntry.FillMaxWidth
                "fillMaxHeight" -> ModifierEntry.FillMaxHeight
                "fillMaxSize" -> ModifierEntry.FillMaxSize
                "weight" -> ModifierEntry.Weight(
                    weight = parseNumber(args["__pos0"])
                )
                "background" -> ModifierEntry.Background(
                    color = args["color"] ?: args["__pos0"],
                    shape = args["shape"]
                )
                "clip" -> ModifierEntry.Clip(
                    shape = args["__pos0"]
                )
                "border" -> ModifierEntry.Border(
                    width = parseNumber(args["width"]),
                    color = args["color"],
                    shape = args["shape"]
                )
                "clickable" -> ModifierEntry.Clickable(
                    enabled = args["enabled"]?.let { it != "false" } ?: true
                )
                "defaultMinSize" -> ModifierEntry.DefaultMinSize(
                    minWidth = parseNumber(args["minWidth"]),
                    minHeight = parseNumber(args["minHeight"])
                )
                "widthIn" -> ModifierEntry.WidthIn(
                    min = parseNumber(args["min"]),
                    max = parseNumber(args["max"])
                )
                "heightIn" -> ModifierEntry.HeightIn(
                    min = parseNumber(args["min"]),
                    max = parseNumber(args["max"])
                )
                "offset" -> ModifierEntry.Offset(
                    x = parseNumber(args["x"]),
                    y = parseNumber(args["y"])
                )
                "alpha" -> ModifierEntry.Alpha(
                    value = parseNumber(args["__pos0"]) ?: NumberModel(1f)
                )
                "zIndex" -> ModifierEntry.ZIndex(
                    value = parseNumber(args["__pos0"]) ?: NumberModel(0f)
                )
                "rotate" -> ModifierEntry.Rotate(
                    degrees = parseNumber(args["__pos0"]) ?: NumberModel(0f)
                )
                "scale" -> ModifierEntry.Scale(
                    scale = parseNumber(args["__pos0"]) ?: NumberModel(1f)
                )
                else -> ModifierEntry.UnknownModifier("$modName(${args.values.joinToString(", ")})")
            }

            entries.add(entry)
        }

        return ModifierModel(entries)
    }

    // ── Utility methods ──

    private fun String.takeUntilCommaOrEnd(start: Int): Pair<String, Int> {
        var i = start
        var depth = 0
        while (i < length) {
            when (this[i]) {
                '(' -> depth++
                ')' -> { if (depth == 0) return Pair(substring(start, i), i); depth-- }
                ',' -> { if (depth == 0) return Pair(substring(start, i), i + 1) }
                '}' -> { if (depth == 0) return Pair(substring(start, i), i) }
            }
            i++
        }
        return Pair(substring(start, i), i)
    }

    private fun skipToCommaOrEnd(content: String, p: Int): Int {
        var i = p
        var depth = 0
        while (i < content.length) {
            when (content[i]) {
                '(' -> depth++
                ')' -> { if (depth == 0) return i; depth-- }
                ',' -> { if (depth == 0) return i + 1 }
                '{' -> { val end = findMatchingBrace(content, i); if (end == -1) return content.length; i = end }
            }
            i++
        }
        return i
    }

    private fun parseIdentifier(content: String, start: Int): String? {
        if (start >= content.length) return null
        val ch = content[start]
        if (ch != '_' && !ch.isLetter()) return null
        val end = findIdentifierEnd(content, start)
        return if (end > start) content.substring(start, end) else null
    }

    private fun findIdentifierEnd(content: String, start: Int): Int {
        var i = start
        while (i < content.length && (content[i].isLetterOrDigit() || content[i] == '_')) i++
        return i
    }

    private fun findStringEnd(content: String, start: Int): Int {
        var i = start + 1
        while (i < content.length) {
            when (content[i]) {
                '\\' -> i += 2  // skip escape sequence
                '"' -> return i + 1
                '\n' -> return i
                else -> i++
            }
        }
        return content.length
    }

    private fun findValueEnd(content: String, start: Int): Int {
        var i = start
        var depth = 0
        while (i < content.length) {
            when (content[i]) {
                '(' -> depth++
                ')' -> { if (depth == 0) return i; depth-- }
                ',' -> { if (depth == 0) return i }
                '}' -> { if (depth == 0) return i }
                ' ', '\t', '\n', '\r' -> { if (depth == 0) return i }
                '{' -> { val end = findMatchingBrace(content, i); if (end == -1) return i; i = end }
            }
            i++
        }
        return i
    }

    private fun skipWhitespaceAndComments(content: String, start: Int): Int {
        var i = start
        while (i < content.length) {
            when {
                content[i] == ' ' || content[i] == '\t' || content[i] == '\n' || content[i] == '\r' -> i++
                content[i] == '/' && i + 1 < content.length && content[i + 1] == '/' -> {
                    val end = content.indexOf('\n', i)
                    i = if (end == -1) content.length else end + 1
                }
                content[i] == '/' && i + 1 < content.length && content[i + 1] == '*' -> {
                    val end = content.indexOf("*/", i + 2)
                    i = if (end == -1) content.length else end + 2
                }
                else -> break
            }
        }
        return i
    }

    private fun findMatchingParen(content: String, start: Int): Int {
        return findMatchingInBlock(content, start, '(', ')')
    }

    private fun findMatchingBrace(content: String, start: Int): Int {
        return findMatchingInBlock(content, start, '{', '}')
    }

    private fun findMatchingInBlock(content: String, start: Int, open: Char, close: Char): Int {
        if (start >= content.length || content[start] != open) return -1
        var depth = 0
        var i = start
        var inString = false
        var inComment = false

        while (i < content.length) {
            val ch = content[i]

            if (inString) {
                if (ch == '\\') i += 2
                else if (ch == '"') inString = false
                i++
                continue
            }

            if (ch == '"' && !inString) {
                inString = true
                i++
                continue
            }

            if (ch == open) depth++
            else if (ch == close) {
                depth--
                if (depth == 0) return i
            }
            i++
        }

        return -1
    }

    private fun findExpressionEnd(content: String, start: Int): Int {
        var i = start
        var depth = 0
        while (i < content.length) {
            when (content[i]) {
                '(' -> depth++
                ')' -> { if (depth == 0) return i; depth-- }
                '{' -> { val end = findMatchingBrace(content, i); if (end == -1) return i; i = end }
                ';', '\n' -> { if (depth == 0) return i }
                '}', ')' -> { if (depth == 0) return i }
            }
            i++
        }
        return i
    }

    private fun skipToChar(content: String, start: Int, vararg chars: Char): Int {
        var i = start
        while (i < content.length) {
            if (content[i] in chars) return i
            i++
        }
        return -1
    }

    private fun skipWhitespace(content: String, start: Int): Int {
        var i = start
        while (i < content.length && (content[i] == ' ' || content[i] == '\t' || content[i] == '\n' || content[i] == '\r')) i++
        return i
    }

    private fun extractLine(pos: Int): String {
        val lineStart = source.lastIndexOf('\n', pos).let { if (it == -1) 0 else it + 1 }
        val lineEnd = source.indexOf('\n', pos).let { if (it == -1) source.length else it }
        return source.substring(lineStart, lineEnd).trim()
    }

    private fun parseParameters(start: Int, end: Int): List<String> {
        val params = mutableListOf<String>()
        var p = start + 1
        while (p < end) {
            p = skipWhitespaceAndComments(source, p)
            if (p >= end) break
            val paramEnd = skipToChars(source, p, ',', ')')
            val param = source.substring(p, min(paramEnd, end)).trim()
            if (param.isNotBlank()) params.add(param)
            p = paramEnd + 1
        }
        return params
    }

    private fun skipToChars(content: String, start: Int, vararg chars: Char): Int {
        var i = start
        var depth = 0
        while (i < content.length) {
            when (content[i]) {
                '(' -> depth++
                ')' -> { if (depth == 0 && ')' in chars) return i; depth-- }
                ',' -> { if (depth == 0 && ',' in chars) return i }
                in chars -> { if (depth == 0) return i }
            }
            i++
        }
        return content.length
    }

    private fun parseNumber(value: String?): NumberModel? {
        if (value == null) return null
        return NumberModel.parse(value)
    }

    private fun extractStringArg(args: Map<String, String>, key: String): String? {
        val value = args[key] ?: return null
        // Remove surrounding quotes
        val trimmed = value.trim()
        return when {
            trimmed.startsWith("\"") && trimmed.endsWith("\"") ->
                trimmed.substring(1, trimmed.length - 1)
            trimmed.startsWith("'") && trimmed.endsWith("'") ->
                trimmed.substring(1, trimmed.length - 1)
            else -> trimmed
        }
    }
}

data class ParsedComposable(
    val name: String,
    val hasPreviewAnnotation: Boolean,
    val parameters: List<String>,
    val body: List<UiNode>,
    val bodyStart: Int,
    val bodyEnd: Int
)
