package com.example.myapplication

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val timestampProvider = TimestampProvider.getInstance()
    private val TAG = "TimestampDemo"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Log timestamps to verify functionality
        Log.d(TAG, "Absolute time: ${timestampProvider.getAbsoluteTime()}")
        Log.d(TAG, "Relative time: ${timestampProvider.getRelativeTimeMillis()} ms")
        
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    TimestampDemo(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun TimestampDemo(modifier: Modifier = Modifier) {
    val timestampProvider = remember { TimestampProvider.getInstance() }
    var absoluteTime by remember { mutableStateOf(timestampProvider.getAbsoluteTime()) }
    var relativeTime by remember { mutableStateOf(timestampProvider.getRelativeTimeMillis()) }
    
    // Update timestamps every second
    LaunchedEffect(Unit) {
        while (true) {
            absoluteTime = timestampProvider.getAbsoluteTime()
            relativeTime = timestampProvider.getRelativeTimeMillis()
            delay(1000)
        }
    }
    
    Column(
        modifier = modifier.padding(16.dp)
    ) {
        Text(
            text = "TimestampProvider Demo",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Text(
            text = "Absolute Time:",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = absoluteTime,
            style = MaterialTheme.typography.bodyMedium
        )
        
        Text(
            text = "Relative Time:",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = "$relativeTime ms",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Preview(showBackground = true)
@Composable
fun TimestampDemoPreview() {
    MyApplicationTheme {
        TimestampDemo()
    }
}
