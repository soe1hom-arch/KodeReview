# KodeReview — Live Kotlin & Compose Code Reviewer + Live Preview

An Android app that performs **real-time code review** AND **live Compose UI preview** directly on your device.

## ✨ Features

### 🔍 Live Code Review
- **Real-time Analysis** — Type code and get instant feedback with 500ms debounce
- **7+ Detection Rules** — Covers Compose best practices and Kotlin conventions
- **Inline Diagnostics** — Squiggly underlines, line number indicators with color-coded severity, full diagnostic panel
- **Syntax Highlighting** — Custom regex-based Kotlin highlighter with Catppuccin Mocha theme

### 🎨 Live Compose Preview
- **Parse → Render Pipeline** — Extracts UI tree from source and renders it as live Compose UI
- **15+ Components Supported** — Column, Row, Box, Text, Button, Card, Surface, LazyColumn, and more
- **Modifier Support** — padding, size, fillMaxWidth, background, border, weight, clip, clickable, and more
- **Split View** — See code and preview side-by-side simultaneously

**How it works:**
```
Source Code → ComposePreviewParser (recursive descent) → UiNode Tree → ComposeRenderer → Live Compose UI
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
- **Custom Syntax Highlighter** — regex-based tokenization (no compiler dependency needed)
- **Custom Recursive Descent Parser** for Compose UI tree extraction
- Android SDK 34, Min SDK 26
- Build with GitHub Actions CI/CD

## 📱 Usage

1. **Open the app** — sample code is pre-loaded with issues to demonstrate
2. **Code tab** — write/edit Kotlin with real-time syntax highlighting and analysis
3. **Preview tab** — see live rendered Compose UI from your code
4. **Split tab** — code and preview side by side
5. **Open .kt files** — tap the folder icon to browse and open Kotlin files from device storage
6. **Tap issues in gutter** — jump directly to error lines
7. **Diagnostic panel** — scrollable bottom panel with issue details and suggestions
8. **Reset** — load the sample code anytime with refresh button

## 🚀 Build & Download

### GitHub Actions (recommended)
Push to GitHub — workflow builds APK automatically.
Download the latest APK from the **Actions** tab → select latest run → **KodeReview-APK** artifact.

### Local build
```bash
git clone https://github.com/soe1hom-arch/KodeReview
cd KodeReview
./gradlew assembleDebug
# APK at: app/build/outputs/apk/debug/app-debug.apk
```

## 🏗️ Architecture

```
┌──────────────────────────────────────────┐
│  UI (Jetpack Compose)                    │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ │
│  │ Editor   │ │Preview   │ │Diagnostic│ │
│  │ (Code)   │ │(Render)  │ │ Panel    │ │
│  └────┬─────┘ └────┬─────┘ └──────────┘ │
└───────┼─────────────┼────────────────────┘
        │             │
┌───────┴──────┐ ┌────┴──────────────┐
│ Analyzer     │ │ Preview Parser    │
│ Engine       │ │ (Recursive        │
│ (Custom      │ │  Descent)         │
│  Rules)      │ │ → UiNode Tree     │
└──────────────┘ └───────────────────┘
```

## 📁 Project Structure

```
CodeReviewApp/
├── .github/workflows/build.yml         ← GitHub Actions CI
├── app/src/main/java/com/kodereview/app/
│   ├── MainActivity.kt                 ← Entry point + file picker
│   ├── ui/
│   │   ├── theme/                      ← Catppuccin Mocha dark theme
│   │   └── screen/
│   │       ├── EditorScreen.kt         ← Tabs: Code / Preview / Split
│   │       └── components/
│   │           ├── CodeEditor.kt       ← Syntax-highlighted editor
│   │           ├── DiagnosticPanel.kt  ← Bottom issues panel
│   │           └── PreviewPanel.kt     ← Live Compose preview
│   ├── highlighter/
│   │   └── KotlinSyntaxHighlighter.kt  ← Regex-based highlighter
│   ├── analyzer/                       ← Static analysis engine + rules
│   ├── preview/
│   │   ├── ComposePreviewParser.kt     ← Recursive descent parser
│   │   └── ComposePreviewRenderer.kt   ← UiNode → Compose rendering
│   └── model/                          ← Data models
└── build files
```
