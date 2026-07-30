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

    EditorScreen(
        key = editorKey,
        initialCode = currentCode,
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
