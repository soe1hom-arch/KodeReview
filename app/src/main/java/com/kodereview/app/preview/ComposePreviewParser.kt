package com.kodereview.app.preview

import com.kodereview.app.model.*
import kotlin.math.min

/**
 * Recursive descent parser that extracts a Compose UI tree from Kotlin source code.
 *
 * Handles a practical subset of Compose components for live preview.
 * Improved to handle control flow, complex annotations, and unknown patterns gracefully.
 */
class ComposePreviewParser(
    private val source: String,
    private val isActive: () -> Boolean = { true },
    private val timeoutMs: Long = 0L
) {
    private var parseIterations = 0
    private val deadlineNanos: Long = if (timeoutMs > 0) System.nanoTime() + timeoutMs * 1_000_000 else 0L

    /** Custom composables found in this source, keyed by lowercased name. */
    private var customComposables: Map<String, ParsedComposable> = emptyMap()

    /** Current custom-composable inlining depth (prevents infinite recursion). */
    private var inlineDepth = 0

    private val MAX_INLINE_DEPTH = 8
    
    /** Check cancellation and iteration limits. Throws CancellationException if cancelled. */
    private fun ensureActive() {
        if (!isActive()) throw kotlinx.coroutines.CancellationException("Parser cancelled")
        parseIterations++
        if (parseIterations > 2_000_000) {
            throw IllegalStateException("Parser exceeded maximum iteration limit")
        }
        if (deadlineNanos > 0L && System.nanoTime() > deadlineNanos) {
            throw IllegalStateException("Parse timed out after ${timeoutMs}ms")
        }
    }

    /**
     * Known Compose composable function names that we can render.
     */
    private val KNOWN_COMPOSABLES = setOf(
        "Column", "Row", "Box", "Text", "Button", "OutlinedButton", "TextButton",
        "Icon", "Image", "Spacer", "Divider", "Surface", "Card",
        "CircularProgressIndicator", "LinearProgressIndicator",
        "LazyColumn", "LazyRow", "Scaffold",
        "TopAppBar", "CenterAlignedTopAppBar",
        "NavigationBar", "NavigationBarItem",
        "ModalNavigationDrawer", "ModalDrawerSheet",
        "Switch", "Checkbox", "RadioButton", "Slider",
        "AlertDialog", "Dialog",
        "IconButton", "Text", "FloatingActionButton",
        "SmallFloatingActionButton", "LargeFloatingActionButton",
        "ExtendedFloatingActionButton",
        "DropdownMenu", "DropdownMenuItem",
        "Badge", "BadgedBox",
        "BottomSheetScaffold", "BottomSheet",
        "Tab", "TabRow",
        "Card", "ElevatedCard", "OutlinedCard",
        "ModalBottomSheet",
        "SearchBar", "DockedSearchBar",
        "PullToRefreshBox",
        "HorizontalDivider", "VerticalDivider"
    )

    /**
     * Non-UI calls that should be skipped entirely (no node rendered).
     */
    private val NON_UI_CALLS = setOf(
        "launchedeffect", "disposableeffect", "sideeffect",
        "remember", "remembercoroutinescope", "rememberdrawerstate",
        "rememberscrollstate", "rememberlazyliststate", "rememberlazycolumnstate",
        "rememberlazylistitemsstate", "remembersaveable", "derivedstateof",
        "remembermutablestateof", "mutableintstateof", "mutablelongstateof",
        "mutablefloatstateof", "mutabledoublestateof", "mutablebooleanstateof",
        "producestate", "snapshotflow", "animationstateof", "rememberanimatable",
        "rememberinfiniteTransition", "collectasstate", "collectasstatewithlifecycle",
        "collect", "launch", "delay", "updatestate", "rememberupdatedstate"
    )

    fun parseAll(): List<ParsedComposable> {
        // Pass 1: parse without inlining to discover all custom composables.
        val firstPass = parseAllRaw()
        if (firstPass.isEmpty()) return firstPass

        customComposables = firstPass.associateBy { it.name.lowercase() }
        try {
            // Pass 2: re-parse with custom composables available for inlining.
            return parseAllRaw()
        } finally {
            customComposables = emptyMap()
        }
    }

    private fun parseAllRaw(): List<ParsedComposable> {
        val composables = mutableListOf<ParsedComposable>()
        var searchPos = 0

        while (true) {
            ensureActive()
            val composable = parseNextComposable(searchPos) ?: break
            composables.add(composable)
            searchPos = composable.bodyEnd
        }

        return composables
    }

    fun parseForPreview(): ParsedComposable? {
        val all = parseAll()
        if (all.isEmpty()) return null
        val preview = all.find { it.hasPreviewAnnotation }
        return preview ?: all.first()
    }

    /**
     * Parse the composable at/after startPos. Returns null if no more found.
     */
    private fun parseNextComposable(startPos: Int): ParsedComposable? {
        var searchPos = startPos
        val sourceLen = source.length

        while (searchPos < sourceLen) {
            ensureActive()
            // Skip past import statements
            searchPos = skipImports(searchPos)

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

            // Check if it's actually a function (not "function" in a string)
            if (name.isEmpty()) return null

            // Find opening parenthesis for parameters
            val paramStart = source.indexOf('(', afterFun)
            if (paramStart == -1) return null

            // Find matching closing paren
            val paramEnd = findMatchingParen(source, paramStart)
            if (paramEnd == -1) return null

            // Parse parameters
            val params = parseParameters(paramStart, paramEnd)

            // Find body
            var bodySearchStart = paramEnd + 1
            val afterParen = skipWhitespace(source, bodySearchStart)
            var bodyStart = afterParen

            // Check for return type ": Type"
            if (bodyStart < sourceLen && source[bodyStart] == ':') {
                val afterColon = skipWhitespace(source, bodyStart + 1)
                val typeEnd = findExpressionEnd(source, afterColon)
                bodyStart = typeEnd
                bodyStart = skipWhitespace(source, bodyStart)
            }

            // Find opening brace for body
            if (bodyStart >= sourceLen || source[bodyStart] != '=') {
                // Normal body with braces
                val bracePos = skipToChar(source, bodyStart, '{')
                if (bracePos == -1) return null

                bodyStart = bracePos
                val bodyEnd = findMatchingBrace(source, bodyStart)
                if (bodyEnd == -1) return null

                val bodyContent = source.substring(bodyStart + 1, bodyEnd)
                val nodes = parseBody(bodyContent, bodyStart + 1)

                return ParsedComposable(
                    name = name,
                    hasPreviewAnnotation = hasPreview,
                    parameters = params,
                    body = nodes,
                    bodyStart = bodyStart,
                    bodyEnd = bodyEnd + 1
                )
            } else {
                // Expression body: fun name() = expression
                val eqPos = bodyStart
                val exprEnd = findExpressionEnd(source, eqPos + 1)
                val bodyContent = source.substring(eqPos + 1, exprEnd)
                val nodes = parseBody(bodyContent, eqPos + 1)

                return ParsedComposable(
                    name = name,
                    hasPreviewAnnotation = hasPreview,
                    parameters = params,
                    body = nodes,
                    bodyStart = bodyStart,
                    bodyEnd = exprEnd + 1
                )
            }
        }

        return null
    }

    /**
     * Skip import statements at the beginning.
     */
    private fun skipImports(start: Int): Int {
        var pos = start
        while (pos < source.length) {

            ensureActive()
            val ch = source[pos]
            if (ch == 'i' && source.regionMatches(pos, "import ", 0, 7)) {
                val endOfLine = source.indexOf('\n', pos)
                pos = if (endOfLine == -1) source.length else endOfLine + 1
            } else if (ch == 'p' && source.regionMatches(pos, "package ", 0, 8)) {
                val endOfLine = source.indexOf('\n', pos)
                pos = if (endOfLine == -1) source.length else endOfLine + 1
            } else if (ch == '/' && pos + 1 < source.length && source[pos + 1] == '/') {
                val endOfLine = source.indexOf('\n', pos)
                pos = if (endOfLine == -1) source.length else endOfLine + 1
            } else if (ch == '/' && pos + 1 < source.length && source[pos + 1] == '*') {
                val endOfComment = source.indexOf("*/", pos + 2)
                pos = if (endOfComment == -1) source.length else endOfComment + 2
            } else if (ch == '\n' || ch == '\r' || ch == ' ' || ch == '\t') {
                pos++
            } else {
                break
            }
        }
        return pos
    }

    /**
     * Parse the body of a composable function, extracting UI nodes.
     * Handles various Kotlin constructs gracefully.
     */
    private fun parseBody(content: String, offset: Int): List<UiNode> {
        val nodes = mutableListOf<UiNode>()
        var p = 0

        while (p < content.length) {
            ensureActive()
            p = skipWhitespaceAndComments(content, p)
            if (p >= content.length) break

            val ch = content[p]

            // End of current block
            if (ch == '}') break

            // Skip package / import (shouldn't be in body, but just in case)
            if ((ch == 'i' && content.regionMatches(p, "import ", 0, 7)) ||
                (ch == 'p' && content.regionMatches(p, "package ", 0, 8))) {
                val eol = content.indexOf('\n', p)
                p = if (eol == -1) content.length else eol + 1
                continue
            }

            // Variable/state declarations
            if ((ch == 'v' && (content.regionMatches(p, "val ", 0, 4) || content.regionMatches(p, "var ", 0, 4))) ||
                (ch == 'c' && content.regionMatches(p, "const ", 0, 6)) ||
                (ch == 'l' && content.regionMatches(p, "lateinit ", 0, 9))
            ) {
                // Skip to end of statement - handles multi-line declarations with blocks
                p = skipStatementEnd(content, p)
                continue
            }

            // catch / finally blocks following try - skip them
            if ((ch == 'c' && content.regionMatches(p, "catch", 0, 5)) ||
                (ch == 'f' && content.regionMatches(p, "finally", 0, 7))
            ) {
                p = skipControlFlowBlock(content, p)
                continue
            }

            // Control flow: if / when / for / while / try
            if (isControlFlowStart(content, p)) {
                p = skipControlFlowBlock(content, p)
                continue
            }

            // Scope functions: .let { }, .apply { }, .run { }, .also { }, .with(...) { }
            if (isScopeFunctionStart(content, p)) {
                p = skipScopeFunction(content, p)
                continue
            }

            // Return statement
            if (ch == 'r' && content.regionMatches(p, "return", 0, 6)) {
                val eol = content.indexOf('\n', p)
                p = if (eol == -1) content.length else eol + 1
                continue
            }

            // Annotation (e.g., @Composable inside a function?)
            if (ch == '@') {
                val eol = content.indexOf('\n', p)
                p = if (eol == -1) content.length else eol + 1
                continue
            }

            // Try to parse a composable call
            val node = tryParseCall(content, p, offset)
            if (node != null) {
                nodes.add(node.first)
                p = node.second
            } else {
                // Skip line to recover
                val newLine = content.indexOf('\n', p)
                p = if (newLine == -1) content.length else newLine + 1
            }
        }

        return nodes
    }

    /**
     * Check if the code at position p is the start of a control flow construct.
     */
    private fun isControlFlowStart(content: String, p: Int): Boolean {
        return (content.regionMatches(p, "if ", 0, 3) ||
                content.regionMatches(p, "if(", 0, 3) ||
                content.regionMatches(p, "when ", 0, 5) ||
                content.regionMatches(p, "when(", 0, 5) ||
                content.regionMatches(p, "for ", 0, 4) ||
                content.regionMatches(p, "for(", 0, 4) ||
                content.regionMatches(p, "while ", 0, 6) ||
                content.regionMatches(p, "while(", 0, 6) ||
                content.regionMatches(p, "try ", 0, 4) ||
                content.regionMatches(p, "try{", 0, 4))
    }

    /**
     * Skip a control flow block (if/when/for/while/try).
     * Returns the new position after the block.
     */
    private fun skipControlFlowBlock(content: String, p: Int): Int {
        var i = p
        // Skip to the first '{' that starts the block body
        while (i < content.length) {
            ensureActive()
            when (content[i]) {
                '{' -> {
                    val end = findMatchingBrace(content, i)
                    return if (end == -1) content.length else end + 1
                }
                ';', '\n' -> {
                    // No brace block, single line
                    return i + 1
                }
                '(' -> {
                    // Skip parenthesized condition
                    val close = findMatchingParen(content, i)
                    if (close == -1) return content.length
                    i = close + 1
                }
                else -> i++
            }
        }
        return content.length
    }

    /**
     * Check if at a scope function start (.let, .apply, .run, .also, .with).
     */
    private fun isScopeFunctionStart(content: String, p: Int): Boolean {
        return (content.regionMatches(p, ".let", 0, 4) ||
                content.regionMatches(p, ".apply", 0, 6) ||
                content.regionMatches(p, ".run", 0, 4) ||
                content.regionMatches(p, ".also", 0, 5) ||
                content.regionMatches(p, ".with", 0, 5) ||
                content.regionMatches(p, ".takeIf", 0, 7) ||
                content.regionMatches(p, ".takeUnless", 0, 11))
    }

    /**
     * Skip a scope function call.
     */
    private fun skipScopeFunction(content: String, p: Int): Int {
        var i = p
        // Find the opening brace or paren
        while (i < content.length) {
            ensureActive()
            when (content[i]) {
                '{' -> {
                    val end = findMatchingBrace(content, i)
                    return if (end == -1) content.length else end + 1
                }
                '(' -> {
                    val close = findMatchingParen(content, i)
                    if (close == -1) return content.length
                    i = close + 1
                }
                ';', '\n' -> return i + 1
                else -> i++
            }
        }
        return content.length
    }

    /**
     * Try to parse a composable call at position p.
     */
    private fun tryParseCall(content: String, p: Int, offset: Int): Pair<UiNode, Int>? {
        var pos = p
        pos = skipWhitespaceAndComments(content, pos)
        if (pos >= content.length) return null

        // Check for identifiers like "Modifier." - skip those
        if (content.regionMatches(pos, "Modifier.", 0, 9)) {
            return null
        }

        // Read identifier (composable name)
        val nameStart = pos
        val name = parseIdentifier(content, pos) ?: return null
        pos += name.length

        pos = skipWhitespaceAndComments(content, pos)
        if (pos >= content.length) return null

        val ch = content[pos]
        if (ch != '(' && ch != '{') return null

        // Parse arguments
        val namedArgs = mutableMapOf<String, String>()
        var hasTrailingLambda = false
        var contentBody: String? = null

        if (ch == '(') {
            val closeParen = findMatchingParen(content, pos)
            if (closeParen == -1) return null

            val argsStr = content.substring(pos + 1, closeParen)
            val args = parseArguments(argsStr)
            namedArgs.putAll(args)

            pos = closeParen + 1
            pos = skipWhitespaceAndComments(content, pos)
        }

        // Check for trailing lambda { ... }
        if (pos < content.length && content[pos] == '{') {
            val bodyStart = pos
            val bodyEnd = findMatchingBrace(content, pos)
            if (bodyEnd != -1) {
                contentBody = content.substring(bodyStart + 1, bodyEnd)
                pos = bodyEnd + 1
                hasTrailingLambda = true
            }
        }

        // Skip known non-UI calls entirely (LaunchedEffect, remember, etc.)
        if (name.lowercase() in NON_UI_CALLS) {
            return Pair(UiNode.Noop, pos)
        }

        // Inline custom composables defined in this source file.
        if (name.lowercase() in customComposables) {
            val custom = customComposables[name.lowercase()] ?: return Pair(UiNode.Noop, pos)
            val mergedArgs = LinkedHashMap(namedArgs)
            if (contentBody != null && !mergedArgs.containsKey("content")) {
                mergedArgs["content"] = "{ $contentBody }"
            }
            val node = inlineCustomComposable(custom, mergedArgs)
            return Pair(node, pos)
        }

        // If it's a known composable, build the node
        if (name in KNOWN_COMPOSABLES || name[0].isUpperCase()) {
            val node = buildUiNode(name, namedArgs, contentBody, offset)
            return Pair(node, pos)
        }

        // Unknown function, skip it
        return null
    }

    /**
     * Inline a custom composable's body, substituting its parameters with the
     * call-site argument values.
     */
    private fun inlineCustomComposable(
        custom: ParsedComposable,
        args: Map<String, String>
    ): UiNode {
        if (inlineDepth >= MAX_INLINE_DEPTH) {
            return UiNode.Unknown(name = custom.name, error = "Max inline depth")
        }

        val customStart = custom.bodyStart
        val customEnd = custom.bodyEnd
        if (customStart + 1 >= customEnd) {
            return UiNode.Unknown(name = custom.name, error = "Empty body")
        }

        val bodyText = source.substring(customStart + 1, customEnd - 1)
        val substituted = substituteParams(bodyText, custom.parameters, args)

        inlineDepth++
        try {
            val nodes = parseBody(substituted, 0)
            return if (nodes.isEmpty()) {
                UiNode.Unknown(name = custom.name, error = "Empty body")
            } else {
                UiNode.Unknown(name = custom.name, children = nodes)
            }
        } finally {
            inlineDepth--
        }
    }

    /**
     * Substitute composable parameter names with call-site argument values.
     * Uses temporary placeholders to avoid cascading replacement.
     */
    private fun substituteParams(
        bodyText: String,
        params: List<String>,
        args: Map<String, String>
    ): String {
        val paramValues = LinkedHashMap<String, String>()
        var positional = 0
        for (param in params) {
            val name = extractParamName(param)
            if (name != null) {
                val value = args[name] ?: args["__pos$positional"]
                if (value != null) {
                    paramValues[name] = value
                }
                positional++
            }
        }
        if (paramValues.isEmpty()) return bodyText

        var result = bodyText
        val placeholders = mutableListOf<Pair<String, String>>()
        var idx = 0
        for ((name, value) in paramValues) {
            val ph = "\u0001${idx++}\u0002"
            placeholders.add(ph to value)
            result = result.replace(Regex("\\b" + Regex.escape(name) + "\\b"), ph)
        }
        for ((ph, value) in placeholders) {
            result = result.replace(ph, value)
        }
        return result
    }

    /**
     * Extract the bare parameter name from a Kotlin parameter declaration
     * like `label: String`, `selected: Boolean = false`, or `@Composable content: () -> Unit`.
     */
    private fun extractParamName(param: String): String? {
        var p = param.trim()
        if (p.startsWith("val ") || p.startsWith("var ")) p = p.substringAfter(" ").trim()
        // Strip annotations like @Composable
        while (p.startsWith("@")) {
            val space = p.indexOf(' ')
            if (space == -1) return null
            p = p.substring(space + 1).trim()
        }
        val colon = p.indexOf(':')
        val eq = p.indexOf('=')
        val end = when {
            colon >= 0 && eq >= 0 -> minOf(colon, eq)
            colon >= 0 -> colon
            eq >= 0 -> eq
            else -> p.length
        }
        val name = p.substring(0, end).trim()
        if (name.isEmpty() || !name.all { it.isLetterOrDigit() || it == '_' }) return null
        if (name[0].isDigit()) return null
        return name
    }

    /**
     * Parse arguments string into key-value pairs.
     * Handles strings, lambdas, expressions, and nested parens/braces.
     */
    private fun parseArguments(argsStr: String): Map<String, String> {
        val args = mutableMapOf<String, String>()
        var p = 0
        var positionalCount = 0

        while (p < argsStr.length) {
            p = skipWhitespaceAndComments(argsStr, p)

            ensureActive()
            if (p >= argsStr.length) break

            // Find key (identifier before '=')
            val keyEnd = skipToChars(argsStr, p, '=', ',', ')')
            val key = argsStr.substring(p, keyEnd).trim()
            p = keyEnd

            if (p < argsStr.length && argsStr[p] == '=') {
                p++ // skip '='
                p = skipWhitespaceAndComments(argsStr, p)
                if (p >= argsStr.length) break

                val (value, newP) = parseArgumentValue(argsStr, p)
                if (key.isNotEmpty()) {
                    args[key] = value.trim()
                }
                p = newP
            } else if (key.isNotEmpty()) {
                // Positional argument
                args["__pos${positionalCount}"] = key
                positionalCount++
                p = keyEnd
            }

            // Skip comma
            if (p < argsStr.length && argsStr[p] == ',') p++
            else if (p < argsStr.length && argsStr[p] == ')') break
        }

        return args
    }

    /**
     * Parse an argument value: string, number, lambda, or expression.
     */
    private fun parseArgumentValue(content: String, start: Int): Pair<String, Int> {
        var p = skipWhitespaceAndComments(content, start)
        if (p >= content.length) return Pair("", p)

        val ch = content[p]

        return when {
            // String literal
            ch == '"' -> {
                val end = findStringEnd(content, p)
                Pair(content.substring(p, end), end)
            }
            // Character literal
            ch == '\'' -> {
                val charEnd = if (p + 2 < content.length && content[p + 1] == '\\') p + 4 else p + 2
                Pair(content.substring(p, charEnd.coerceAtMost(content.length)), charEnd)
            }
            // Lambda / block
            ch == '{' -> {
                val end = findMatchingBrace(content, p)
                if (end != -1) Pair(content.substring(p, end + 1), end + 1)
                else Pair(content.substring(p), content.length)
            }
            // Lambda argument shorthand: { it ... }
            ch == '(' && content.regionMatches(p, "({", 0, 2) -> {
                val end = findMatchingParen(content, p)
                if (end != -1) Pair(content.substring(p, end + 1), end + 1)
                else Pair(content.substring(p), content.length)
            }
            // Number or expression
            else -> {
                val end = findValueEnd(content, p)
                Pair(content.substring(p, end), end)
            }
        }
    }

    /**
     * Build a UiNode from parsed composable call data.
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
        val buttonText = text ?: extractTextFromNodeList(children)

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
            "Card", "ElevatedCard", "OutlinedCard" -> UiNode.Card(
                modifier = modifier,
                children = children
            )
            "Text" -> UiNode.Text(
                modifier = modifier,
                text = text ?: extractStringArg(args, "__pos0") ?: "",
                color = args["color"],
                fontSize = parseNumber(args["fontSize"]),
                fontWeight = args["fontWeight"],
                maxLines = args["maxLines"]?.toIntOrNull(),
                textAlign = args["textAlign"]
            )
            "Button" -> UiNode.Button(
                modifier = modifier,
                text = buttonText,
                onClickAvailable = args.containsKey("onClick"),
                enabled = args["enabled"]?.let { it != "false" } ?: true
            )
            "OutlinedButton", "FilledTonalButton" -> UiNode.OutlinedButton(
                modifier = modifier,
                text = buttonText
            )
            "TextButton" -> UiNode.TextButton(
                modifier = modifier,
                text = buttonText
            )
            "IconButton" -> UiNode.IconButton(
                modifier = modifier,
                text = text ?: ""
            )
            "Icon" -> UiNode.Icon(
                modifier = modifier,
                name = args["imageVector"] ?: args["painter"] ?: args["bitmap"] ?: args["__pos0"],
                tint = args["tint"]
            )
            "Image" -> UiNode.Image(
                modifier = modifier,
                painterName = args["painter"] ?: args["bitmap"],
                contentDescription = extractStringArg(args, "contentDescription") ?: "",
                contentScale = args["contentScale"]
            )
            "Spacer" -> UiNode.Spacer(modifier = modifier)
            "Divider", "HorizontalDivider" -> UiNode.Divider(
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
                content = children.firstOrNull(),
                topBar = parseLambdaBody(args["topBar"]),
                bottomBar = parseLambdaBody(args["bottomBar"])
            )
            "TopAppBar", "CenterAlignedTopAppBar" -> UiNode.TopAppBar(
                modifier = modifier,
                title = extractTextFromLambda(args["title"])
                    ?: extractStringArg(args, "title")
                    ?: extractStringArg(args, "__pos0")
                    ?: ""
            )
            "NavigationBar" -> UiNode.NavigationBar(
                modifier = modifier,
                children = children
            )
            "NavigationBarItem" -> UiNode.NavigationBarItem(
                modifier = modifier,
                selected = args["selected"]?.let { it.toBooleanStrictOrNull() } ?: false,
                label = extractTextFromLambda(args["label"])
                    ?: extractStringArg(args, "label")
                    ?: extractStringArg(args, "__pos0")
                    ?: text
                    ?: ""
            )
            "Switch" -> UiNode.Switch(
                modifier = modifier,
                checked = args["checked"]?.let { it.toBooleanStrictOrNull() } ?: false
            )
            "Checkbox" -> UiNode.Checkbox(
                modifier = modifier,
                checked = args["checked"]?.let { it.toBooleanStrictOrNull() } ?: false
            )
            "AlertDialog", "Dialog" -> UiNode.Dialog(
                modifier = modifier,
                title = extractStringArg(args, "title") ?: extractStringArg(args, "__pos0"),
                text = extractStringArg(args, "text") ?: extractStringArg(args, "__pos1"),
                children = children
            )
            "ModalNavigationDrawer" -> UiNode.ModalNavigationDrawer(
                modifier = modifier,
                content = children.firstOrNull(),
                drawerContent = parseLambdaBody(args["drawerContent"])
            )
            "ModalDrawerSheet" -> UiNode.ModalDrawerSheet(
                modifier = modifier,
                children = children
            )
            "FloatingActionButton", "SmallFloatingActionButton",
            "LargeFloatingActionButton", "ExtendedFloatingActionButton" -> UiNode.FloatingActionButton(
                modifier = modifier,
                text = text ?: name
            )
            else -> UiNode.Unknown(
                modifier = modifier,
                name = name,
                error = if (children.isNotEmpty() || text != null) null else "Unknown component: $name",
                children = children
            )
        }
    }

    // ── Modifier Parser ──

    private fun parseModifier(modifierStr: String): ModifierModel {
        val entries = mutableListOf<ModifierEntry>()
        if (modifierStr.isBlank()) return ModifierModel(entries)

        var p = 0
        if (modifierStr.startsWith("Modifier")) {
            p = 8
        }

        while (p < modifierStr.length) {
            p = skipWhitespaceAndComments(modifierStr, p)

            ensureActive()
            if (p >= modifierStr.length || modifierStr[p] != '.') break

            p++ // skip '.'

            val nameStart = p
            val nameEnd = findIdentifierEnd(modifierStr, p)
            if (nameEnd <= nameStart) break
            val modName = modifierStr.substring(nameStart, nameEnd)
            p = nameEnd

            p = skipWhitespaceAndComments(modifierStr, p)
            if (p >= modifierStr.length || modifierStr[p] != '(') {
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

            val entry = when (modName) {
                "size" -> ModifierEntry.Size(
                    width = parseNumber(args["width"] ?: args["__pos0"]),
                    height = parseNumber(args["height"] ?: args["__pos1"])
                )
                "width" -> ModifierEntry.Width(value = parseNumber(args["__pos0"]) ?: NumberModel(0f))
                "height" -> ModifierEntry.Height(value = parseNumber(args["__pos0"]) ?: NumberModel(0f))
                "padding" -> ModifierEntry.Padding(
                    all = parseNumber(args["all"] ?: args["__pos0"]),
                    start = parseNumber(args["start"]), end = parseNumber(args["end"]),
                    top = parseNumber(args["top"]), bottom = parseNumber(args["bottom"]),
                    horizontal = parseNumber(args["horizontal"]), vertical = parseNumber(args["vertical"])
                )
                "fillMaxWidth" -> ModifierEntry.FillMaxWidth
                "fillMaxHeight" -> ModifierEntry.FillMaxHeight
                "fillMaxSize" -> ModifierEntry.FillMaxSize
                "weight" -> ModifierEntry.Weight(weight = parseNumber(args["__pos0"]))
                "background" -> ModifierEntry.Background(color = args["color"] ?: args["__pos0"], shape = args["shape"])
                "clip" -> ModifierEntry.Clip(shape = args["__pos0"])
                "border" -> ModifierEntry.Border(width = parseNumber(args["width"]), color = args["color"], shape = args["shape"])
                "clickable" -> ModifierEntry.Clickable(enabled = args["enabled"]?.let { it != "false" } ?: true)
                "defaultMinSize" -> ModifierEntry.DefaultMinSize(minWidth = parseNumber(args["minWidth"]), minHeight = parseNumber(args["minHeight"]))
                "widthIn" -> ModifierEntry.WidthIn(min = parseNumber(args["min"]), max = parseNumber(args["max"]))
                "heightIn" -> ModifierEntry.HeightIn(min = parseNumber(args["min"]), max = parseNumber(args["max"]))
                "offset" -> ModifierEntry.Offset(x = parseNumber(args["x"]), y = parseNumber(args["y"]))
                "alpha" -> ModifierEntry.Alpha(value = parseNumber(args["__pos0"]) ?: NumberModel(1f))
                "zIndex" -> ModifierEntry.ZIndex(value = parseNumber(args["__pos0"]) ?: NumberModel(0f))
                "rotate" -> ModifierEntry.Rotate(degrees = parseNumber(args["__pos0"]) ?: NumberModel(0f))
                "scale" -> ModifierEntry.Scale(scale = parseNumber(args["__pos0"]) ?: NumberModel(1f))
                "matchParentSize" -> ModifierEntry.FillMaxSize
                else -> ModifierEntry.UnknownModifier("$modName(${args.values.joinToString(", ")})")
            }

            entries.add(entry)
        }

        return ModifierModel(entries)
    }

    // ── Utility Methods ──

    private fun String.takeUntilCommaOrEnd(start: Int): Pair<String, Int> {
        var i = start
        var depth = 0
        while (i < length) {

            ensureActive()
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
            ensureActive()
            when (content[i]) {
                '(' -> depth++
                ')' -> { if (depth == 0) return i; depth-- }
                ',' -> { if (depth == 0) return i }
                '}' -> { if (depth == 0) return i }
            }
            i++
        }
        return content.length
    }

    private fun parseIdentifier(content: String, start: Int): String? {
        val end = findIdentifierEnd(content, start)
        return if (end > start) content.substring(start, end) else null
    }

    private fun findIdentifierEnd(content: String, start: Int): Int {
        var i = start
        if (i >= content.length) return start
        // First char must be letter, underscore, or backtick
        if (content[i] != '_' && !content[i].isLetter() && content[i] != '`') return start
        i++
        if (start < content.length && content[start] == '`') {
            // Backtick-quoted identifier
            while (i < content.length && content[i] != '`') i++
            return if (i < content.length) i + 1 else start
        }
        while (i < content.length && (content[i].isLetterOrDigit() || content[i] == '_')) i++
        return i
    }

    private fun findStringEnd(content: String, start: Int): Int {
        var i = start + 1
        while (i < content.length) {
            ensureActive()
            when (content[i]) {
                '\\' -> i += 2
                '"' -> return i + 1
                '$' -> {
                    // String template: ${...} or $identifier
                    if (i + 1 < content.length && content[i + 1] == '{') {
                        val braceEnd = findMatchingBrace(content, i + 1)
                        i = if (braceEnd != -1) braceEnd + 1 else i + 2
                    } else {
                        i++
                    }
                }
                else -> i++
            }
        }
        return content.length
    }

    private fun findValueEnd(content: String, start: Int): Int {
        var i = start
        var depth = 0
        while (i < content.length) {
            ensureActive()
            when (content[i]) {
                '(' -> { depth++; i++ }
                ')' -> {
                    if (depth == 0) return i
                    depth--; i++
                }
                ',' -> { if (depth == 0) return i; i++ }
                '}' -> { if (depth == 0) return i; i++ }
                ' ', '\t', '\n', '\r' -> {
                    // Stop at whitespace if we have content before it
                    if (i > start && depth == 0) {
                        // Check if next non-whitespace is a valid continuation
                        val nextNonSpace = skipWhitespace(content, i)
                        if (nextNonSpace < content.length && 
                            (content[nextNonSpace] == '.' || content[nextNonSpace] == '?' || content[nextNonSpace] == '!' || content[nextNonSpace] == ':')) {
                            i = nextNonSpace // Don't break, it's a chain continuation
                        } else {
                            return i
                        }
                    } else {
                        i++
                    }
                }
                else -> i++
            }
        }
        return content.length
    }

    /**
     * Skip a val/var/const declaration to the end of the statement,
     * handling multi-line blocks like `val x = remember { ... }`.
     */
    private fun skipStatementEnd(content: String, start: Int): Int {
        var i = start
        var depth = 0
        var inString = false
        while (i < content.length) {
            ensureActive()
            val ch = content[i]
            if (inString) {
                if (ch == '\\') { i += 2; continue }
                else if (ch == '"') inString = false
                else if (ch == '$' && i + 1 < content.length && content[i + 1] == '{') {
                    val braceEnd = findMatchingBrace(content, i + 1)
                    if (braceEnd != -1) i = braceEnd
                }
                i++
                continue
            }
            when {
                ch == '"' -> inString = true
                ch == '/' && i + 1 < content.length && content[i + 1] == '/' -> {
                    val end = content.indexOf('\n', i)
                    i = if (end == -1) content.length else end
                }
                ch == '/' && i + 1 < content.length && content[i + 1] == '*' -> {
                    val end = content.indexOf("*/", i + 2)
                    i = if (end == -1) content.length else end + 2
                    continue
                }
                ch == '{' -> depth++
                ch == '}' -> {
                    depth--
                    if (depth < 0) return i + 1
                }
                ch == '\n' -> {
                    if (depth == 0) return i + 1
                }
            }
            i++
        }
        return content.length
    }

    /**
     * Parse a lambda argument value like `{ Column { ... } }` into its child nodes.
     * Returns null if the value is not a brace lambda.
     */
    private fun parseLambdaBody(argValue: String?): UiNode? {
        if (argValue == null) return null
        val trimmed = argValue.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null
        val inner = trimmed.substring(1, trimmed.length - 1)
        // Strip lambda parameter arrow like `{ padding -> ... }`
        val arrowIdx = inner.indexOf("->")
        val body = if (arrowIdx != -1 && inner.substring(0, arrowIdx).contains(" ")) {
            inner.substring(arrowIdx + 2)
        } else inner
        val nodes = parseBody(body, 0)
        return nodes.firstOrNull()
    }

    private fun skipWhitespaceAndComments(content: String, start: Int): Int {
        var i = start
        while (i < content.length) {
            ensureActive()
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

        while (i < content.length) {
            val ch = content[i]

            ensureActive()
            if (inString) {
                if (ch == '\\') { i += 2; continue }
                else if (ch == '"') inString = false
                else if (ch == '$' && i + 1 < content.length && content[i + 1] == '{') {
                    // Skip string template ${...}
                    val braceEnd = findMatchingBrace(content, i + 1)
                    if (braceEnd != -1) i = braceEnd
                }
                i++
                continue
            }

            when {
                ch == '"' -> inString = true
                ch == '/' && i + 1 < content.length && content[i + 1] == '/' -> {
                    val end = content.indexOf('\n', i)
                    i = if (end == -1) content.length else end
                }
                ch == '/' && i + 1 < content.length && content[i + 1] == '*' -> {
                    val end = content.indexOf("*/", i + 2)
                    i = if (end == -1) content.length else end + 2
                    continue
                }
                ch == open -> depth++
                ch == close -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }

        return -1
    }

    private fun findExpressionEnd(content: String, start: Int): Int {
        var i = start
        var depth = 0
        while (i < content.length) {
            ensureActive()
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

            ensureActive()
            if (content[i] in chars) return i
            i++
        }
        return -1
    }

    private fun skipWhitespace(content: String, start: Int): Int {
        var i = start
        while (i < content.length && (content[i] == ' ' || content[i] == '\t' || content[i] == '\n' || content[i] == '\r')) {
            ensureActive()
            i++
        }
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

            ensureActive()
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
        var inString = false

        while (i < content.length) {
            ensureActive()
            val ch = content[i]
            if (inString) {
                if (ch == '\\') { i += 2; continue }
                else if (ch == '"') inString = false
                i++
                continue
            }
            when (ch) {
                '"' -> inString = true
                '(' -> depth++
                ')' -> { if (depth == 0 && ')' in chars) return i; depth-- }
                '{' -> { 
                    // Skip braces
                    val end = findMatchingBrace(content, i)
                    if (end != -1) i = end
                }
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
        val trimmed = value.trim()
        return when {
            trimmed.startsWith("\"") && trimmed.endsWith("\"") ->
                trimmed.substring(1, trimmed.length - 1)
            trimmed.startsWith("'") && trimmed.endsWith("'") ->
                trimmed.substring(1, trimmed.length - 1)
            else -> trimmed
        }
    }

    private fun extractTextFromNodeList(children: List<UiNode>): String {
        for (child in children) {
            when (child) {
                is UiNode.Text -> return child.text
                is UiNode.Column -> extractTextFromNodeList(child.children).takeIf { it.isNotEmpty() }?.let { return it }
                is UiNode.Row -> extractTextFromNodeList(child.children).takeIf { it.isNotEmpty() }?.let { return it }
                is UiNode.Box -> extractTextFromNodeList(child.children).takeIf { it.isNotEmpty() }?.let { return it }
                is UiNode.Surface -> extractTextFromNodeList(child.children).takeIf { it.isNotEmpty() }?.let { return it }
                is UiNode.Card -> extractTextFromNodeList(child.children).takeIf { it.isNotEmpty() }?.let { return it }
                is UiNode.Unknown -> extractTextFromNodeList(child.children).takeIf { it.isNotEmpty() }?.let { return it }
                else -> {}
            }
        }
        return ""
    }

    /**
     * Extract the text of the first Text node inside a lambda argument like
     * `{ Column { Text("AFFT") } }`. Returns null if none found.
     */
    private fun extractTextFromLambda(argValue: String?): String? {
        if (argValue == null) return null
        val trimmed = argValue.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null
        val inner = trimmed.substring(1, trimmed.length - 1)
        val arrowIdx = inner.indexOf("->")
        val body = if (arrowIdx != -1 && inner.substring(0, arrowIdx).contains(" ")) {
            inner.substring(arrowIdx + 2)
        } else inner
        val nodes = parseBody(body, 0)
        return extractTextFromNodeList(nodes).ifEmpty { null }
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
