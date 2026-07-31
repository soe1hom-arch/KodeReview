import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.kodereview.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kodereview.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "1.2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }
}

// ---------------------------------------------------------------------------
// Live preview assets: resources needed at runtime to compile user code on
// device (Kotlin compiler classpath, Compose compiler plugin, android.jar).
// ---------------------------------------------------------------------------

val composeCompilerPluginVersion = "1.5.4"
val livePreviewAssetsDir = layout.buildDirectory.dir("generated/livepreview")

val composeCompilerPlugin by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

dependencies {
    composeCompilerPlugin("androidx.compose.compiler:compiler:$composeCompilerPluginVersion")
}

// Register the generated asset directory so its files land inside the APK.
android.sourceSets.getByName("main").assets.srcDir(livePreviewAssetsDir)

val prepareLivePreviewAssets by tasks.registering {
    // App classes must be compiled first so they can be included in the
    // compile classpath for user code.
    dependsOn(tasks.named("compileDebugKotlin"))
    dependsOn(tasks.named("compileDebugJavaWithJavac"))

    val config = composeCompilerPlugin
    val assetsDir = livePreviewAssetsDir.get().asFile
    val sdkDirectory = android.sdkDirectory

    inputs.files(config)
    inputs.file(provider { File(sdkDirectory, "platforms/android-34/android.jar") })
    outputs.dir(assetsDir)

    doLast {
        val liveDir = File(assetsDir, "live")
        liveDir.mkdirs()

        // 1) Compose compiler plugin jar
        val pluginFile = config.singleFile
        File(liveDir, "compose-compiler.jar").let {
            if (!it.exists() || it.length() != pluginFile.length()) {
                pluginFile.copyTo(it, overwrite = true)
            }
        }

        // 2) android.jar (used by D8 as --lib and by the compiler as rt.jar)
        val androidJar = File(sdkDirectory, "platforms/android-34/android.jar")
        if (!androidJar.isFile) {
            throw GradleException("android.jar not found at $androidJar")
        }
        File(liveDir, "android.jar").let {
            if (!it.exists() || it.length() != androidJar.length()) {
                androidJar.copyTo(it, overwrite = true)
            }
        }

        // 3) compile-classpath.jar: every .class from the app runtime classpath
        // plus the app's own compiled classes.
        val cpJar = File(liveDir, "compile-classpath.jar")
        val tmp = File(liveDir, "cp.tmp")
        if (tmp.exists()) tmp.deleteRecursively()
        tmp.mkdirs()
        val sources = mutableListOf<File>()
        val appRuntimeClasspath = configurations.findByName("runtimeClasspath")
            ?: configurations.getByName("debugRuntimeClasspath")
        sources += appRuntimeClasspath.files.filter { it.isFile }
        sources += File(project.layout.buildDirectory.get().asFile, "tmp/kotlin-classes/debug")
        sources += File(project.layout.buildDirectory.get().asFile, "intermediates/javac/debug/classes")

        // Never expose the on-device compiler/R8 internals to user code.
        val excludedPrefixes = listOf(
            "org/jetbrains/kotlin/cli/",
            "org/jetbrains/kotlin/compilerPlugins/",
            "org/jetbrains/kotlin/psi/",
            "org/jetbrains/kotlin/backend/",
            "org/jetbrains/kotlin/codegen/",
            "org/jetbrains/kotlin/resolve/",
            "org/jetbrains/kotlin/ir/",
            "org/jetbrains/kotlin/analysis/",
            "org/jetbrains/kotlin/com/",
            "org/jetbrains/kotlin/util/",
            "org/jetbrains/kotlin/config/",
            "org/jetbrains/kotlin/types/",
            "org/jetbrains/kotlin/descriptors/",
            "org/jetbrains/kotlin/name/",
            "org/jetbrains/kotlin/metadata/",
            "org/jetbrains/kotlin/load/",
            "org/jetbrains/kotlin/lexer/",
            "org/jetbrains/kotlin/parsing/",
            "org/jetbrains/kotlin/utils/",
            "org/jetbrains/kotlin/checkers/",
            "org/jetbrains/kotlin/builtins/",
            "org/jetbrains/kotlin/diagnostics/",
            "org/jetbrains/kotlin/idea/",
            "org/jetbrains/kotlin/progress/",
            "org/jetbrains/kotlin/context/",
            "org/jetbrains/kotlin/evaluate/",
            "org/jetbrains/kotlin/plugin/",
            "org/jetbrains/objectweb/",
            "org/jetbrains/intellij/",
            "com/android/tools/r8/",
            "kotlin/script/",
            "kotlinx/metadata/",
            "org/tartarus/",
            "com/google/common/"
        )
        fun excluded(name: String): Boolean =
            excludedPrefixes.any { name.startsWith(it) }

        val seen = HashSet<String>()
        ZipOutputStream(cpJar.outputStream().buffered()).use { zout ->
            fun addEntry(rel: String, input: () -> java.io.InputStream) {
                if (!rel.endsWith(".class") || excluded(rel)) return
                if (!seen.add(rel)) return
                zout.putNextEntry(ZipEntry(rel))
                input().use { it.copyTo(zout) }
                zout.closeEntry()
            }
            for (src in sources) {
                if (!src.exists()) continue
                if (src.isDirectory) {
                    src.walkTopDown().filter { it.isFile }.forEach {
                        addEntry(it.relativeTo(src).path.replace(File.separatorChar, '/')) { it.inputStream() }
                    }
                } else {
                    ZipFile(src).use { zf ->
                        val entries = zf.entries()
                        while (entries.hasMoreElements()) {
                            val e = entries.nextElement()
                            if (e.isDirectory) continue
                            addEntry(e.name) { zf.getInputStream(e) }
                        }
                    }
                }
            }
        }
        tmp.deleteRecursively()
    }
}

afterEvaluate {
    tasks.matching { it.name == "mergeDebugAssets" || it.name == "mergeReleaseAssets" }
        .configureEach { dependsOn(prepareLivePreviewAssets) }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.01.00")
    implementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.core:core-ktx:1.12.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // On-device Kotlin compilation (Android Studio-style live preview)
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.9.20")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.9.20")
    implementation("com.android.tools:r8:8.2.33")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
