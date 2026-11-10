package com.kenkern.engine

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
 *
 * Orientation is managed by the screens themselves (see [EditorOrientation]):
 * portrait base state from the manifest, landscape while the editor is open.
 *
 * Note: the window is intentionally NOT edge-to-edge (no `enableEdgeToEdge`).
 * The GL viewport composites behind a translucent window; keeping the system
 * bars system-painted (theme colors) avoids side-inset overlap of editor
 * chrome in landscape and works uniformly on all supported API levels.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KenkernTheme {
                EditorRoot()
            }
        }
    }
}
