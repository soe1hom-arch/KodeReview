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

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "KodeReview"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            enableEdgeToEdge()
            setContent {
                KodeReviewTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = EditorBackground
                    ) {
                        ErrorBoundary {
                            KodeReviewApp(activity = this@MainActivity)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error during onCreate", e)
            // Fallback: show error on screen
            setContent {
                KodeReviewTheme {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Error: ${e.message ?: "Unknown error"}\n\nPlease restart the app.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}

/**
 * Simple error boundary composable that catches crashes and shows a fallback UI
 * instead of letting the app crash.
 */
@Composable
fun ErrorBoundary(
    fallback: @Composable (Throwable) -> Unit = { error ->
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Something went wrong:\n${error.message ?: "Unknown error"}",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    },
    content: @Composable () -> Unit
) {
    var error by remember { mutableStateOf<Throwable?>(null) }
    if (error != null) {
        fallback(error!!)
    } else {
        try {
            content()
        } catch (e: Exception) {
            Log.e("KodeReview", "Composition error", e)
            error = e
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
