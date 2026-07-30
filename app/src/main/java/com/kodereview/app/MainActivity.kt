package com.kodereview.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.kodereview.app.ui.screen.EditorScreen
import com.kodereview.app.ui.theme.EditorBackground
import com.kodereview.app.ui.theme.KodeReviewTheme

private const val TAG = "KodeReview"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KodeReviewTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = EditorBackground
                ) {
                    KodeReviewApp(activity = this@MainActivity)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleViewIntent(intent)
    }

    private fun handleViewIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW && intent.data != null) {
            try {
                val uri = intent.data!!
                val inputStream = contentResolver.openInputStream(uri)
                val text = inputStream?.bufferedReader()?.readText() ?: ""
                inputStream?.close()

                val cursor = contentResolver.query(uri, null, null, null, null)
                val fileName = cursor?.use {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && it.moveToFirst()) it.getString(nameIndex) else null
                } ?: "file.kt"

                Log.d(TAG, "Intent opened: $fileName (${text.length} chars)")
                if (text.isNotBlank()) {
                    SampleHolder.code = text
                    SampleHolder.version++
                }
            } catch (e: Exception) {
                Log.e(TAG, "Intent data error", e)
            }
        }
    }
}

@Composable
fun KodeReviewApp(activity: ComponentActivity) {
    var editorKey by remember { mutableStateOf(0) }

    // Handle initial intent
    LaunchedEffect(Unit) {
        val intent = activity.intent
        if (intent?.action == Intent.ACTION_VIEW && intent.data != null) {
            try {
                val uri = intent.data!!
                val inputStream = activity.contentResolver.openInputStream(uri)
                val text = inputStream?.bufferedReader()?.readText() ?: ""
                inputStream?.close()

                val cursor = activity.contentResolver.query(uri, null, null, null, null)
                val fileName = cursor?.use {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && it.moveToFirst()) it.getString(nameIndex) else null
                } ?: "file.kt"

                Log.d(TAG, "Initial intent opened: $fileName (${text.length} chars)")
                if (text.isNotBlank()) {
                    SampleHolder.code = text
                    editorKey++
                }
            } catch (e: Exception) {
                Log.e(TAG, "Initial intent data error", e)
            }
        }
    }

    // Observe SampleHolder version changes
    LaunchedEffect(SampleHolder.version) {
        if (SampleHolder.code != null) {
            editorKey++
        }
    }

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = activity.contentResolver.openInputStream(uri)
                val text = inputStream?.bufferedReader()?.readText() ?: ""
                inputStream?.close()

                val cursor = activity.contentResolver.query(uri, null, null, null, null)
                val fileName = cursor?.use {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && it.moveToFirst()) it.getString(nameIndex) else null
                } ?: "file.kt"

                Log.d(TAG, "File picker opened: $fileName (${text.length} chars)")
                if (text.isNotBlank()) {
                    SampleHolder.code = text
                    SampleHolder.version++
                }
            } catch (e: Exception) {
                Log.e(TAG, "File read error", e)
            }
        }
    }

    val initialCode = SampleHolder.code
    EditorScreen(
        key = editorKey,
        initialCode = initialCode,
        onPickFile = {
            // Try multiple mime types for .kt files
            try {
                filePickerLauncher.launch(arrayOf(
                    "text/plain",
                    "text/x-kotlin",
                    "text/x-java",
                    "*/*"
                ))
            } catch (e: Exception) {
                Log.e(TAG, "File picker launch error", e)
            }
        }
    )
}

object SampleHolder {
    var code: String? = null
    var version: Int = 0
}
