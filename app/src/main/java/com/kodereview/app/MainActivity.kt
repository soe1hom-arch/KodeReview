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
import com.kodereview.app.ui.screen.EditorScreen
import com.kodereview.app.ui.theme.EditorBackground
import com.kodereview.app.ui.theme.KodeReviewTheme

private const val TAG = "KodeReview"

class MainActivity : ComponentActivity() {
    /**
     * Observable state holding text from incoming VIEW intents.
     * Composable will observe this and load new code when it changes.
     */
    private val _pendingCode = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check initial intent
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
        // onNewIntent means activity is already running, new intent arrived
        readIntentIfAvailable(intent)
    }

    private fun readIntentIfAvailable(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW || intent.data == null) return
        try {
            val uri = intent.data!!
            val inputStream = contentResolver.openInputStream(uri)
            val text = inputStream?.bufferedReader()?.readText()
            inputStream?.close()

            if (text != null && text.isNotBlank()) {
                val cursor = contentResolver.query(uri, null, null, null, null)
                val fileName = cursor?.use {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && it.moveToFirst()) it.getString(nameIndex) else null
                } ?: "file.kt"
                Log.d(TAG, "Intent loaded: $fileName (${text.length} chars)")
                _pendingCode.value = text
            } else {
                Log.w(TAG, "Intent returned empty text")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Intent read error", e)
        }
    }
}

@Composable
fun KodeReviewApp(
    activity: ComponentActivity,
    pendingCode: MutableState<String?>
) {
    var editorKey by remember { mutableStateOf(0) }
    var currentCode by remember { mutableStateOf(pendingCode.value) }

    // Observe pendingCode changes (from intents)
    LaunchedEffect(pendingCode.value) {
        val code = pendingCode.value
        if (code != null && code.isNotBlank()) {
            currentCode = code
            editorKey++
            pendingCode.value = null  // Consume the pending code
            Toast.makeText(activity, "File loaded (${code.length} chars)", Toast.LENGTH_SHORT).show()
        }
    }

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = activity.contentResolver.openInputStream(uri)
                val text = inputStream?.bufferedReader()?.readText()
                inputStream?.close()

                if (text != null && text.isNotBlank()) {
                    currentCode = text
                    editorKey++
                    Toast.makeText(activity, "File loaded (${text.length} chars)", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(activity, "File is empty", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "File read error", e)
                Toast.makeText(activity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    EditorScreen(
        key = editorKey,
        initialCode = currentCode,
        onPickFile = {
            try {
                // "*/*" accepts all file types
                filePickerLauncher.launch("*/*")
            } catch (e: Exception) {
                Log.e(TAG, "File picker error", e)
                Toast.makeText(activity, "Cannot open file picker", Toast.LENGTH_SHORT).show()
            }
        }
    )
}
