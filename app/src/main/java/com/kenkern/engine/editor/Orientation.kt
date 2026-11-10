package com.kenkern.engine.editor

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo

/**
 * Orientation policy of the KENKERN editor.
 *
 * The launcher (home/project list) is portrait; the 3D scene editor requests
 * landscape. The switch is performed through [Activity.setRequestedOrientation]
 * — the platform mechanism — combined with the `configChanges` declaration in
 * the manifest, so the (single) activity is never recreated on the transition:
 * scene state, the GL surface host and floating panels survive it intact.
 */
object EditorOrientation {

    /** Orientation requested while the editor screen is visible. */
    const val EDITOR: Int = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

    /** Orientation restored for the home/launcher screen. */
    const val LAUNCHER: Int = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
}

/** Unwraps [Context] wrappers (Compose may hand a `ContextWrapper`) to find the host Activity. */
fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
