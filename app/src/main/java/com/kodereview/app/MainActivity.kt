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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kodereview.app.ui.screen.EditorScreen
import com.kodereview.app.ui.theme.EditorBackground
import com.kodereview.app.ui.theme.KodeReviewTheme

private const val TAG = "KodeReview"

/**
 * Error state holder for app-level crash recovery.
 * Set to true via setUnhandledExceptionHandler if a crash occurs.
 */
object AppErrorState {
    var lastError: String? = null
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set thread-level exception handler for catching Java/Kotlin crashes
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception on ${thread.name}", throwable)
            AppErrorState.lastError = throwable.message ?: throwable.javaClass.simpleName
        }

        enableEdgeToEdge()
        setContent {
            KodeReviewTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = EditorBackground
                ) {
                    // Check for uncaught errors and show fallback if needed
                    val errorMsg = AppErrorState.lastError
                    if (errorMsg != null) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Error: $errorMsg\n\nTap here to restart",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    } else {
                        KodeReviewApp(activity = this@MainActivity)
                    }
                }
            }
        }
    }
}

@Composable
fun KodeReviewApp(activity: ComponentActivity) {
    var editorKey by remember { mutableStateOf(0) }

    // File picker for .kt files
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

                Log.d(TAG, "Opened: $fileName (${text.length} chars)")
                SampleHolder.code = text
                editorKey++
            } catch (e: Exception) {
                Log.e(TAG, "File read error", e)
            }
        }
    }

    // Handle VIEW intent (open with .kt file)
    LaunchedEffect(activity.intent) {
        try {
            val intent = activity.intent
            if (intent?.action == Intent.ACTION_VIEW && intent.data != null) {
                val inputStream = activity.contentResolver.openInputStream(intent.data!!)
                val text = inputStream?.bufferedReader()?.readText() ?: ""
                inputStream?.close()
                SampleHolder.code = text
                editorKey++
            }
        } catch (e: Exception) {
            Log.e(TAG, "Intent data error", e)
        }
    }

    EditorScreen(
        key = editorKey,
        initialCode = SampleHolder.code,
        onPickFile = {
            filePickerLauncher.launch(arrayOf("text/plain"))
        }
    )
}

/**
 * Simple holder to pass loaded file code across recompositions.
 */
object SampleHolder {
    var code: String? = null
}
