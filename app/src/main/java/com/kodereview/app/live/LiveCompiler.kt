package com.kodereview.app.live

import android.content.Context
import androidx.compose.runtime.Composer
import com.android.tools.r8.CompilationMode
import com.android.tools.r8.D8
import com.android.tools.r8.D8Command
import com.android.tools.r8.OutputMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.ByteBuffer
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Compiles a Kotlin/Compose source file on-device (Android Studio style):
 *
 *   1. Runs the real Kotlin compiler (kotlin-compiler-embeddable) with the
 *      Compose compiler plugin to produce JVM class files.
 *   2. Dexes the class files with D8 (in-process) into a classes.dex.
 *   3. Loads the dex with InMemoryDexClassLoader.
 *   4. Exposes the target composable as a Function2<Composer, Int, Unit>,
 *      which the UI can cast to `@Composable () -> Unit` and pass to
 *      ComposeView.setContent(...) — exactly how the Compose runtime invokes
 *      composable lambdas internally.
 */
object LiveCompiler {

    private const val MIN_API = 26
    private const val COMPOSE_PLUGIN_OPT =
        "plugin:androidx.compose.compiler.plugins.kotlin:suppressKotlinVersionCompatibilityCheck=true"

    sealed class Result {
        data class Ready(
            val composableName: String,
            val invoke: Function2<Composer?, Int, Unit>
        ) : Result()

        data class Failed(val message: String) : Result()
    }

    private data class LiveFiles(
        val workDir: File,
        val composePlugin: File,
        val androidJar: File,
        val compileClasspath: File,
        val fakeJdk: File
    )

    suspend fun compile(
        context: Context,
        mainSource: String,
        extraSources: List<String>,
        targetName: String?
    ): Result = withContext(Dispatchers.Default) {
        try {
            val files = ensureAssets(context)

            val srcDir = File(files.workDir, "src").apply { deleteRecursively(); mkdirs() }
            val classesDir = File(files.workDir, "classes").apply { deleteRecursively(); mkdirs() }
            val dexDir = File(files.workDir, "dex").apply { deleteRecursively(); mkdirs() }

            writeSources(srcDir, mainSource, extraSources)

            val compilerOut = ByteArrayOutputStream()
            val cliArgs = listOf(
                "-d", classesDir.absolutePath,
                "-classpath", files.compileClasspath.absolutePath,
                "-Xplugin=${files.composePlugin.absolutePath}",
                "-P", COMPOSE_PLUGIN_OPT,
                "-jvm-target", "1.8",
                "-jdk-home", files.fakeJdk.absolutePath,
                "-nowarn"
            ) + (srcDir.listFiles()?.map { it.absolutePath } ?: emptyList())
            val exitCode = K2JVMCompiler().exec(PrintStream(compilerOut), *cliArgs.toTypedArray())
            if (exitCode != ExitCode.OK) {
                return@withContext Result.Failed(buildString {
                    append("Kompilasi gagal (exit ${exitCode}).\n")
                    append(compilerOut.toString().trim())
                })
            }
            val compilerMessages = compilerOut.toString().trim()

            val classFiles = classesDir.walkTopDown().filter { it.isFile && it.extension == "class" }.toList()
            if (classFiles.isEmpty()) {
                return@withContext Result.Failed("Tidak ada class yang dihasilkan oleh compiler.\n$compilerMessages")
            }

            // Zip class files so D8 can read them as one program file.
            val classesJar = File(files.workDir, "classes.jar")
            zipClasses(classesJar, classesDir)

            val dexCommand = D8Command.builder()
                .addProgramFiles(classesJar.toPath())
                .addLibraryFiles(files.androidJar.toPath())
                .setMinApiLevel(MIN_API)
                .setMode(CompilationMode.DEBUG)
                .setOutput(dexDir.toPath(), OutputMode.DexIndexed)
                .build()
            D8.run(dexCommand)

            val dexFiles = dexDir.listFiles { f -> f.isFile && f.extension == "dex" }
                ?: emptyArray()
            if (dexFiles.isEmpty()) {
                return@withContext Result.Failed("D8 tidak menghasilkan output dex.")
            }

            val loader = InMemoryDexClassLoader(
                dexFiles.map { ByteBuffer.wrap(it.readBytes()) }.toTypedArray(),
                LiveCompiler::class.java.classLoader
            )

            val classNames = classFiles.mapNotNull { toClassName(classesDir, it) }
            val entry = findComposable(loader, classNames, targetName)
                ?: return@withContext Result.Failed(
                    "Composable '$targetName' tidak ditemukan setelah kompilasi.\n" +
                    "Live mode hanya mendukung composable tanpa argumen (atau dengan default)."
                )

            Result.Ready(entry.first, entry.second)
        } catch (t: Throwable) {
            Result.Failed("Live compiler error:\n${t.stackTraceToString()}")
        }
    }

    // ------------------------------------------------------------------
    // Asset preparation
    // ------------------------------------------------------------------

    private fun ensureAssets(context: Context): LiveFiles {
        val workDir = File(context.filesDir, "live")
        workDir.mkdirs()

        val composePlugin = File(workDir, "compose-compiler.jar")
        val androidJar = File(workDir, "android.jar")
        val compileClasspath = File(workDir, "compile-classpath.jar")

        copyAssetIfNeeded(context, "live/compose-compiler.jar", composePlugin)
        copyAssetIfNeeded(context, "live/android.jar", androidJar)
        copyAssetIfNeeded(context, "live/compile-classpath.jar", compileClasspath)

        // Fake JDK: the on-device compiler needs a JDK-like layout to resolve
        // java.* classes; android.jar doubles as rt.jar (classic trick).
        val fakeJdk = File(workDir, "fakejdk")
        val rtJar = File(File(fakeJdk, "jre/lib"), "rt.jar")
        if (!rtJar.exists() || rtJar.length() != androidJar.length()) {
            rtJar.parentFile?.mkdirs()
            androidJar.copyTo(rtJar, overwrite = true)
        }

        return LiveFiles(workDir, composePlugin, androidJar, compileClasspath, fakeJdk)
    }

    private fun copyAssetIfNeeded(context: Context, assetPath: String, target: File) {
        if (target.exists() && target.length() > 0L) return
        context.assets.open(assetPath).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
    }

    // ------------------------------------------------------------------
    // Sources
    // ------------------------------------------------------------------

    private fun writeSources(srcDir: File, mainSource: String, extraSources: List<String>) {
        File(srcDir, "Main.kt").writeText(mainSource)
        extraSources.forEachIndexed { i, src ->
            File(srcDir, "Ref$i.kt").writeText(src)
        }
    }

    // ------------------------------------------------------------------
    // D8 / class loading
    // ------------------------------------------------------------------

    private fun zipClasses(target: File, classesDir: File) {
        ZipOutputStream(target.outputStream().buffered()).use { zout ->
            classesDir.walkTopDown().filter { it.isFile }.forEach { f ->
                val rel = f.relativeTo(classesDir).path.replace(File.separatorChar, '/')
                zout.putNextEntry(ZipEntry(rel))
                f.inputStream().use { it.copyTo(zout) }
                zout.closeEntry()
            }
        }
    }

    private fun toClassName(root: File, classFile: File): String? {
        val rel = classFile.relativeTo(root).path.removeSuffix(".class")
        return rel.replace(File.separatorChar, '.')
    }

    private fun findComposable(
        loader: ClassLoader,
        classNames: List<String>,
        targetName: String?
    ): Pair<String, Function2<Composer?, Int, Unit>>? {
        val desired = targetName
        for (className in classNames) {
            if (className.contains('$')) continue
            val clazz = try {
                loader.loadClass(className)
            } catch (t: Throwable) {
                continue
            }
            val candidates = clazz.declaredMethods.filter { it.isStatic }
            if (desired != null) {
                // Zero-arg composable: (Composer, int changed)
                val direct = candidates.firstOrNull {
                    it.name == desired && it.parameterTypes.size == 2
                }
                if (direct != null) {
                    return Pair(desired, wrapMethod(direct))
                }
                // Composable where every parameter has a default value:
                // (defaults..., Composer, int changed, int mask)
                val withDefaults = candidates.firstOrNull {
                    it.name == desired &&
                        it.parameterTypes.size >= 3 &&
                        it.parameterTypes[it.parameterTypes.size - 1] == Int::class.java &&
                        it.parameterTypes[it.parameterTypes.size - 2] == Int::class.java &&
                        it.parameterTypes.indexOfFirst { pt -> pt.name == "androidx.compose.runtime.Composer" } != -1
                }
                if (withDefaults != null) {
                    return Pair(desired, wrapDefaultMethod(withDefaults))
                }
            } else {
                candidates.firstOrNull { it.parameterTypes.size == 2 }?.let { m ->
                    return Pair(m.name, wrapMethod(m))
                }
            }
        }
        return null
    }

    private fun wrapMethod(method: java.lang.reflect.Method): Function2<Composer?, Int, Unit> {
        val safe = method.apply { isAccessible = true }
        val fn: Function2<Composer?, Int, Unit> = { composer, changed ->
            safe.invoke(null, composer, changed)
        }
        return fn
    }

    private fun wrapDefaultMethod(method: java.lang.reflect.Method): Function2<Composer?, Int, Unit> {
        val safe = method.apply { isAccessible = true }
        val paramTypes = method.parameterTypes
        val defaultParamCount = paramTypes.size - 3
        // Compose compiler semantics: a set mask bit means "use the default".
        val mask = (1 shl defaultParamCount) - 1
        val fn: Function2<Composer?, Int, Unit> = { composer, changed ->
            val args = arrayOfNulls<Any?>(paramTypes.size)
            for (i in 0 until defaultParamCount) {
                args[i] = if (paramTypes[i].isPrimitive) {
                    java.lang.reflect.Array.get(java.lang.reflect.Array.newInstance(paramTypes[i], 1), 0)
                } else {
                    null
                }
            }
            args[defaultParamCount] = composer
            args[defaultParamCount + 1] = changed
            args[defaultParamCount + 2] = mask
            safe.invoke(null, *args)
        }
        return fn
    }
}
