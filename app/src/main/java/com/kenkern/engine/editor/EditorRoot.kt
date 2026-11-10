package com.kenkern.engine.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * Root navigation of the KENKERN app.
 *
 * The editor intentionally has only two screens in this block:
 * the home screen and the editor shell. Navigation state survives
 * activity recreation via [rememberSaveable].
 */
@Composable
fun EditorRoot() {
    var openProjectId by rememberSaveable { mutableStateOf<String?>(null) }

    val currentProjectId = openProjectId
    if (currentProjectId == null) {
        HomeScreen(onOpenProject = { openProjectId = it })
    } else {
        EditorScreen(
            projectId = currentProjectId,
            onExit = { openProjectId = null }
        )
    }
}
