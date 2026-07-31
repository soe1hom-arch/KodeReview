# KodeReview (Archived)

> Status: **Archived** — 2026-07-31. Pengembangan dihentikan karena fitur "live preview
> ala Android Studio" (kompilasi Kotlin/Compose di perangkat) tidak dapat berjalan stabil
> di Android. Repo dipertahankan apa adanya agar tidak terbuang sia-sia; sebagian besar
> kode editor/analyzer/parser masih layak dipakai ulang di proyek lain.

Android app untuk **mereview kode Kotlin & Jetpack Compose** langsung di HP:
editor dengan syntax highlighting, analisis statis 7 rules, panel diagnostik, file
picker, dan percobaan live preview Compose.

## Status Fitur (per commit terakhir `5c05d52`)

| Fitur | Status | Keterangan |
|-------|--------|------------|
| Code editor + syntax highlighting (Catppuccin Mocha) | ✅ Berfungsi | Regex-based, tanpa dependency compiler |
| Live analysis 7 rules + diagnostic panel color-coded | ✅ Berfungsi | 500ms debounce, gutter + panel |
| 3 tab: Code / Preview / Split | ✅ Berfungsi | — |
| File picker `.kt` dari storage + open via Intent | ✅ Berfungsi | — |
| Preview parser statis (`ComposePreviewParser` → `UiNode` → render) | ⚠️ Terbatas | Render perkiraan struktur UI, jauh dari tampilan asli; tidak dipakai sebagai fitur utama |
| Live compile on-device (`LiveCompiler`: K2JVMCompiler + D8 + `InMemoryDexClassLoader`) | ❌ Gagal di perangkat | Valid di JVM (harness `/tmp/composetest`), tapi di Android masih error; APK jadi ±160 MB |

## Ringkasan Percobaan "Live Compile" (biar yang lanjut tidak mengulang)

- Pipeline: `kotlin-compiler-embeddable 1.9.20` + Compose plugin `1.5.4` → D8 `8.2.33`
  in-process → `InMemoryDexClassLoader` → invoke composable sebagai
  `Function2<Composer, Int, Unit>` → render dengan `ComposeView.setContent`.
- **Fix `PathUtil` INTERNAL_ERROR**: resource lookup jar compiler tidak bisa bekerja di
  Android (class ada di dex). Solusinya meneruskan `-kotlin-home` ke folder berisi jar
  asli (`kotlin-compiler.jar`, `kotlin-stdlib.jar`, `kotlin-reflect.jar`,
  `kotlin-script-runtime.jar`, `trove4j.jar`) yang disalin ke assets saat build
  (lihat `app/build.gradle.kts` → task `prepareLivePreviewAssets`).
- `gradle.properties` butuh heap besar (`-Xmx4096m`) karena aset compiler bikin
  `compressDebugAssets` OOM di heap 2 GB.
- Kendala tersisa yang tidak sempat dituntaskan: error runtime D8/ART di perangkat
  setelah `-kotlin-home` diterapkan.

## Build

### GitHub Actions (cara yang dipakai)
Push ke branch `main` → workflow `build.yml` otomatis build `assembleDebug`.
APK di tab **Actions** → run terakhir → artifact **KodeReview-APK**.

### Lokal
```bash
git clone https://github.com/soe1hom-arch/KodeReview
cd KodeReview
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Struktur

```
app/src/main/java/com/kodereview/app/
├── MainActivity.kt                     # Entry point + file picker + Intent
├── ui/screen/EditorScreen.kt           # Tabs: Code / Preview / Split
├── ui/screen/components/
│   ├── CodeEditor.kt                   # Syntax-highlighted editor
│   ├── DiagnosticPanel.kt              # Panel issue (severity color-coded)
│   └── PreviewPanel.kt                 # Panel preview + tombol ⚡ Live
├── highlighter/KotlinSyntaxHighlighter.kt
├── analyzer/                           # 7 rules static analysis
├── preview/                            # Parser + renderer statis
│   ├── ComposePreviewParser.kt         # Recursive descent → UiNode tree
│   └── ComposePreviewRenderer.kt       # UiNode → Compose UI
├── live/LiveCompiler.kt                # Eksperimen kompilasi on-device (❌)
└── model/                              # Data models + sample code
```

## Stack

Kotlin 1.9.20 · Jetpack Compose (Material 3, BOM 2024.01.00) · AGP 8.2.0 ·
minSdk 26 / targetSdk 34 · GitHub Actions CI
