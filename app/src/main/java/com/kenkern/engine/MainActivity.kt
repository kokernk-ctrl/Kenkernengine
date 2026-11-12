package com.kenkern.engine

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.kenkern.engine.core.CrashDiag
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
 * Note: the window is intentionally NOT edge-to-edge (no `enableEdgeToEdge`)
 * and NOT translucent. The GL viewport composites through the standard
 * SurfaceView punch-hole contract (surface behind an opaque window, viewport
 * region unpainted), which is the most driver-compatible configuration.
 *
 * Every lifecycle transition is recorded to KENKERN_CRASH_DIAG: the
 * scene-entry crash investigation needs the exact lifecycle position at
 * process death.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        CrashDiag.step("ACTIVITY_ON_CREATE", "savedState=${savedInstanceState != null}")
        super.onCreate(savedInstanceState)
        CrashDiag.step("ACTIVITY_SUPER_ON_CREATE_DONE")
        setContent {
            KenkernTheme {
                EditorRoot()
            }
        }
        CrashDiag.step("ACTIVITY_ON_CREATE_END", "orientation=${resources.configuration.orientation}")
    }

    override fun onResume() {
        CrashDiag.step("ACTIVITY_ON_RESUME")
        super.onResume()
    }

    override fun onPause() {
        CrashDiag.step("ACTIVITY_ON_PAUSE")
        super.onPause()
    }

    override fun onDestroy() {
        CrashDiag.step("ACTIVITY_ON_DESTROY", "finishing=$isFinishing")
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        CrashDiag.step("ACTIVITY_CONFIG_CHANGED", "orientation=${newConfig.orientation}")
    }
}
