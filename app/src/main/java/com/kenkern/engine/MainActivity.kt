package com.kenkern.engine

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kenkern.engine.editor.EditorRoot
import com.kenkern.engine.editor.theme.KenkernTheme

/**
 * The single Android activity of the KENKERN editor.
 *
 * All screens (home + editor shell) are Jetpack Compose content hosted here.
 * Configuration changes are declared in the manifest so the activity is not
 * recreated on rotation/resize: this keeps the GL rendering surface alive and
 * the editor state stable. Engine lifecycle-sensitive work observes
 * [androidx.lifecycle.Lifecycle] events directly.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KenkernTheme {
                EditorRoot()
            }
        }
    }
}
