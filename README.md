# KodeReview — Live Kotlin & Compose Code Reviewer + Live Preview

An Android app that performs **real-time code review** AND **live Compose UI preview** directly on your device.

## ✨ Features

### 🔍 Live Code Review
- **Real-time Analysis** — Type code and get instant feedback with 500ms debounce
- **7+ Detection Rules** — Covers Compose best practices and Kotlin conventions
- **Inline Diagnostics** — Squiggly underlines, line number indicators, full diagnostic panel
- **Syntax Highlighting** — Powered by official Kotlin Lexer (Catppuccin Mocha theme)

### 🎨 Live Compose Preview
- **Parse → Render Pipeline** — Extracts UI tree from source and renders live
- **15+ Components Supported** — Column, Row, Box, Text, Button, Image, Card, Surface, and more
- **Modifier Support** — padding, size, fillMaxWidth, background, border, weight, clip, and more
- **Split View** — See code and preview side-by-side

**How it works:**
```
Source Code → ComposePreviewParser (recursive descent) → UiNode Tree → ComposeRenderer → Live UI
```

## 🔍 Detection Rules

| Rule | Category | Description |
|------|----------|-------------|
| Composable Naming | Compose | `@Composable` functions must use PascalCase |
| Modifier Order | Compose | Size modifiers before padding modifiers |
| Remember Usage | Compose | Proper `remember {}` key usage |
| State Hoisting | Compose | Suggest hoisting state for reusability |
| Naming Convention | Kotlin | PascalCase for classes, camelCase for functions |
| Expression Body | Kotlin | Single-expression function syntax |
| Unused Import | Kotlin | Detect potentially unused imports |

## 🖥️ Supported Preview Components

**Layouts:** Column, Row, Box, Surface, Card, Scaffold
**Elements:** Text, Button, OutlinedButton, TextButton, Icon, Image, Spacer, Divider
**Indicators:** CircularProgressIndicator, LinearProgressIndicator
**Lists:** LazyColumn, LazyRow (simplified)

**Modifiers:** size, width, height, padding, fillMaxWidth/Height/Size, weight, background, clip, border, clickable, offset, alpha, zIndex, rotate, scale, defaultMinSize, widthIn, heightIn

## 🛠️ Tech Stack

- **Kotlin** + **Jetpack Compose** (Material 3)
- **Kotlin Compiler Embeddable** 1.9.20 (official lexer for tokenization)
- **Custom Recursive Descent Parser** for Compose UI extraction
- Android SDK 34, Min SDK 26

## 📱 Usage

1. **Open the app** — sample code is pre-loaded with issues to demonstrate
2. **Code tab** — write/edit Kotlin with real-time analysis
3. **Preview tab** — see live rendered Compose UI from your code
4. **Split tab** — code and preview side by side
5. **Open .kt files** — tap the folder icon to browse and open Kotlin files
6. **Tap issues** — jump directly to error lines in code
7. **Reset** — load the sample code anytime

## 🚀 Build

### GitHub Actions (recommended)
Push to GitHub — workflow builds automatically. Download APK from Actions tab.

### Local build (requires Android SDK)
```bash
./gradlew assembleDebug
# APK at: app/build/outputs/apk/debug/app-debug.apk
```

## 🏗️ Architecture

```
┌──────────────────────────────────────────┐
│  UI (Compose)                            │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ │
│  │ Editor   │ │Preview   │ │Diagnostic│ │
│  │ (Code)   │ │(Render)  │ │ Panel    │ │
│  └────┬─────┘ └────┬─────┘ └──────────┘ │
└───────┼─────────────┼────────────────────┘
        │             │
┌───────┴──────┐ ┌────┴──────────────┐
│ Analyzer     │ │ Preview Parser    │
│ Engine       │ │ (Recursive        │
│ (KotlinLexer │ │  Descent)         │
│  + Rules)    │ │ → UiNode Tree     │
└──────────────┘ └───────────────────┘
```

## 📦 Project Structure

```
CodeReviewApp/
├── .github/workflows/build.yml
├── app/src/main/java/com/kodereview/app/
│   ├── MainActivity.kt
│   ├── ui/
│   │   ├── theme/           ← Catppuccin Mocha dark theme
│   │   └── screen/
│   │       ├── EditorScreen.kt        ← Tabs: Code / Preview / Split
│   │       └── components/
│   │           ├── CodeEditor.kt       ← Syntax-highlighted editor
│   │           ├── DiagnosticPanel.kt  ← Bottom issues panel
│   │           └── PreviewPanel.kt     ← Live Compose preview
│   ├── highlighter/
│   │   └── KotlinSyntaxHighlighter.kt ← KotlinLexer-based highlighting
│   ├── analyzer/             ← Static analysis engine + rules
│   ├── preview/
│   │   ├── ComposePreviewParser.kt    ← Recursive descent parser
│   │   └── ComposePreviewRenderer.kt  ← UiNode → Compose rendering
│   └── model/                ← Data models (UiNode, Modifier, EditorState)
└── build files (AGP, Compose, Gradle)
```
