package com.kodereview.app.model

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One .kt file found inside a user-selected project folder (SAF tree). */
data class ProjectFile(
    val uri: Uri,
    val relativePath: String,
    val fileName: String,
    val packageName: String
)

/**
 * Scans a user-selected project folder for .kt files and automatically resolves
 * the `import` statements of the opened file to those files, so the preview can
 * merge theme/component files without selecting them one by one.
 */
object KotlinProjectIndex {

    /** Recursively walks the SAF tree and indexes all .kt files. */
    suspend fun build(context: Context, treeUri: Uri, maxFiles: Int = 600): List<ProjectFile> =
        withContext(Dispatchers.IO) {
            val result = mutableListOf<ProjectFile>()
            val rootDocId = try {
                DocumentsContract.getTreeDocumentId(treeUri)
            } catch (e: Exception) {
                return@withContext result
            }
            walk(context, treeUri, rootDocId, "", result, maxFiles)
            result
        }

    private fun walk(
        context: Context,
        treeUri: Uri,
        docId: String,
        relPath: String,
        out: MutableList<ProjectFile>,
        maxFiles: Int
    ) {
        if (out.size >= maxFiles) return
        val childrenUri = try {
            DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        } catch (e: Exception) {
            return
        }
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        val cursor = try {
            context.contentResolver.query(childrenUri, projection, null, null, null)
        } catch (e: Exception) {
            null
        } ?: return

        cursor.use { c ->
            while (c.moveToNext() && out.size < maxFiles) {
                val childDocId = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                val mime = c.getString(2) ?: ""
                if (DocumentsContract.Document.MIME_TYPE_DIR == mime) {
                    val childRel = if (relPath.isEmpty()) name else "$relPath/$name"
                    walk(context, treeUri, childDocId, childRel, out, maxFiles)
                } else if (name.endsWith(".kt", ignoreCase = true)) {
                    out.add(
                        ProjectFile(
                            uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId),
                            relativePath = if (relPath.isEmpty()) name else "$relPath/$name",
                            fileName = name,
                            packageName = readPackage(context, treeUri, childDocId)
                        )
                    )
                }
            }
        }
    }

    private fun readPackage(context: Context, treeUri: Uri, docId: String): String {
        return try {
            val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            val head = context.contentResolver.openInputStream(uri)?.use { stream ->
                val buf = ByteArray(4096)
                val n = stream.read(buf)
                if (n <= 0) "" else String(buf, 0, n)
            } ?: ""
            Regex("""(?m)^\s*package\s+([\w.]+)""").find(head)?.groupValues?.get(1) ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    /** Reads up to [maxBytes] of a document for cheap content scanning. */
    private fun readFileHead(context: Context, uri: Uri, maxBytes: Int = 200_000): String {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val buf = ByteArray(maxBytes)
                val n = stream.read(buf)
                if (n <= 0) "" else String(buf, 0, n)
            } ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Reads the content of every indexed file referenced by `import` lines in
     * `code`. Wildcard imports (e.g. `com.foo.theme.*`) match all indexed files
     * in that package; plain imports match `package + FileName.kt`.
     */
    suspend fun resolveReferences(
        context: Context,
        code: String,
        projectFiles: List<ProjectFile>
    ): List<ReferenceFile> = withContext(Dispatchers.IO) {
        val seen = LinkedHashSet<String>()
        val out = mutableListOf<ReferenceFile>()
        for (line in code.lineSequence()) {
            val t = line.trim()
            if (!t.startsWith("import ")) continue
            val imp = t.removePrefix("import ").trim().removeSuffix(";")
            if (imp.isEmpty()) continue

            val matches = if (imp.endsWith(".*")) {
                val pkg = imp.removeSuffix(".*")
                projectFiles.filter {
                    it.packageName == pkg || it.packageName.startsWith("$pkg.")
                }
            } else {
                val idx = imp.lastIndexOf('.')
                val pkg = if (idx >= 0) imp.substring(0, idx) else ""
                val name = if (idx >= 0) imp.substring(idx + 1) else imp
                val byName = projectFiles.filter {
                    it.packageName == pkg && it.fileName.equals("$name.kt", ignoreCase = true)
                }
                if (byName.isNotEmpty()) {
                    byName
                } else {
                    // Fallback: the composable may live in another file of the same
                    // package (e.g. ProcessingOverlay is defined in ProgressDialog.kt).
                    projectFiles.filter { pf ->
                        pf.packageName == pkg &&
                            readFileHead(context, pf.uri).contains(Regex("\\bfun\\s+" + Regex.escape(name) + "\\s*\\("))
                    }
                }
            }

            for (pf in matches) {
                if (!seen.add(pf.relativePath)) continue
                val content = try {
                    context.contentResolver.openInputStream(pf.uri)?.bufferedReader()?.use { it.readText() }
                } catch (e: Exception) {
                    null
                }
                if (!content.isNullOrBlank()) {
                    out.add(ReferenceFile(name = pf.fileName, content = content))
                }
            }
        }
        out
    }
}
