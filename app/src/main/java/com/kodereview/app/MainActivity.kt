package com.kodereview.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private const val TAG = "KodeReview"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate start")
        enableEdgeToEdge()
        setContent {
            Log.d(TAG, "setContent rendering")
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFF1E1E2E)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "KodeReview",
                    color = Color(0xFF89B4FA),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Log.d(TAG, "onCreate end")
    }
}
