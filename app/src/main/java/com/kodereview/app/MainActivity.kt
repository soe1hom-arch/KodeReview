package com.kodereview.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.kodereview.app.model.KotlinProjectIndex
import com.kodereview.app.model.ProjectFile
import com.kodereview.app.model.ReferenceFile
import com.kodereview.app.ui.screen.EditorScreen
import kotlinx.coroutines.launch
import com.kodereview.app.ui.theme.EditorBackground
import com.kodereview.app.ui.theme.KodeReviewTheme

private const val TAG = "KodeReview"

class MainActivity : ComponentActivity() {
    private val _pendingCode = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readIntentIfAvailable(intent)
        enableEdgeToEdge()
        setContent {
            KodeReviewTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = EditorBackground
                ) {
                    KodeReviewApp(
                        activity = this@MainActivity,
                        pendingCode = _pendingCode
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readIntentIfAvailable(intent)
    }

    private fun readIntentIfAvailable(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW || intent.data == null) return
        try {
            val uri = intent.data!!
            val text = readTextFromUri(uri)
            if (text != null && text.isNotBlank()) {
                Log.d(TAG, "Intent loaded: ${text.length} chars")
                _pendingCode.value = text
            }
        } catch (e: Exception) {
            Log.e(TAG, "Intent read error", e)
        }
    }

    private fun readTextFromUri(uri: Uri): String? {
        return try {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "Read error: ${e.message}", e)
            null
        }
    }
}

@Composable
fun KodeReviewApp(
    activity: ComponentActivity,
    pendingCode: MutableState<String?>
) {
    var editorKey by remember { mutableStateOf(0) }
    var currentCode by remember { mutableStateOf<String?>(null) }
    var refFiles by remember { mutableStateOf<List<ReferenceFile>>(emptyList()) }
    var projectFiles by remember { mutableStateOf<List<ProjectFile>>(emptyList()) }
    var folderStatus by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Observe pendingCode changes (from intents)
    LaunchedEffect(pendingCode.value) {
        val code = pendingCode.value
        if (code != null && code.isNotBlank()) {
            currentCode = code
            editorKey++
            pendingCode.value = null
            Toast.makeText(activity, "Loaded: ${code.take(30)}... (${code.length} chars)", Toast.LENGTH_SHORT).show()
        }
    }

    // File picker - use OpenDocument for broader file manager support
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val text = activity.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (text != null && text.isNotBlank()) {
                    currentCode = text
                    editorKey++
                    Toast.makeText(activity, "Loaded: ${text.take(30)}... (${text.length} chars)", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(activity, "File is empty", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "File read error", e)
                Toast.makeText(activity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Multi-file picker: add theme/component files as preview references
    val refPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val added = mutableListOf<ReferenceFile>()
        var failed = 0
        for (uri in uris) {
            try {
                val name = displayNameOf(activity.contentResolver, uri) ?: "ref_${added.size + 1}.kt"
                val text = activity.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (!text.isNullOrBlank()) {
                    added.add(ReferenceFile(name = name, content = text))
                } else {
                    failed++
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ref file read error", e)
                failed++
            }
        }
        if (added.isNotEmpty()) {
            refFiles = refFiles + added
            Toast.makeText(
                activity,
                "+${added.size} file referensi (ketuk nama file utk hapus)",
                Toast.LENGTH_SHORT
            ).show()
        }
        if (failed > 0) {
            Toast.makeText(activity, "$failed file gagal dibaca", Toast.LENGTH_SHORT).show()
        }
    }

    // Project folder picker: index all .kt files, then auto-resolve imports
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                activity.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                Log.d(TAG, "Persist permission: ${e.message}")
            }
            val folderName = displayNameOf(activity.contentResolver, uri) ?: "proyek"
            folderStatus = "Memindai folder $folderName…"
            Toast.makeText(activity, "Memindai folder…", Toast.LENGTH_SHORT).show()
            scope.launch {
                val files = KotlinProjectIndex.build(activity, uri)
                projectFiles = files
                folderStatus = "Folder $folderName · ${files.size} file .kt (auto)"
                Toast.makeText(
                    activity,
                    "Folder dimuat: ${files.size} file .kt",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    EditorScreen(
        key = editorKey,
        initialCode = currentCode,
        extraFiles = refFiles,
        projectFiles = projectFiles,
        folderStatus = folderStatus,
        onPickFolder = {
            try {
                folderPickerLauncher.launch(null)
            } catch (e: Exception) {
                Log.e(TAG, "Folder picker error", e)
                Toast.makeText(activity, "Cannot open folder picker: ${e.message}", Toast.LENGTH_LONG).show()
            }
        },
        onAddReferenceFiles = {
            try {
                refPickerLauncher.launch(arrayOf("*/*"))
            } catch (e: Exception) {
                Log.e(TAG, "Ref picker error", e)
                Toast.makeText(activity, "Cannot open file picker: ${e.message}", Toast.LENGTH_LONG).show()
            }
        },
        onRemoveReferenceFile = { index ->
            if (index in refFiles.indices) {
                refFiles = refFiles.filterIndexed { i, _ -> i != index }
            }
        },
        onPickFile = {
            try {
                // Use multiple mime types for broad compatibility
                filePickerLauncher.launch(arrayOf("*/*"))
            } catch (e: Exception) {
                Log.e(TAG, "File picker error", e)
                Toast.makeText(activity, "Cannot open file picker: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    )
}

private fun displayNameOf(resolver: android.content.ContentResolver, uri: Uri): String? {
    return try {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (e: Exception) {
        Log.e(TAG, "Display name error: ${e.message}", e)
        null
    }
}

