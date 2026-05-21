package com.glorycam.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.glorycam.app.camera.CameraViewModel
import com.glorycam.app.camera.GloryCamScreen
import com.glorycam.app.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val cameraViewModel: CameraViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Capture and log any JVM crash to Logcat/stderr for rapid debugging
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("GloryCamCrash", "UNCAUGHT EXCEPTION on thread ${thread.name}", throwable)
            System.err.println("GLORYCAM_CRASH: ${throwable.stackTraceToString()}")
        }

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                GloryCamScreen(
                    viewModel = cameraViewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
