package com.kenkern.engine.editor

import android.app.ActivityManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kenkern.engine.R
import com.kenkern.engine.core.EngineLogger
import com.kenkern.engine.editor.panels.HierarchyPanel
import com.kenkern.engine.editor.panels.InspectorPanel
import com.kenkern.engine.editor.theme.KenkernColors
import com.kenkern.engine.input.ViewportInputHandler
import com.kenkern.engine.rendering.EGLPreflight
import com.kenkern.engine.rendering.GLRenderer
import com.kenkern.engine.rendering.KenkernGLSurfaceView
import kotlinx.coroutines.delay

/**
 * KENKERN editor screen: editor chrome + OpenGL ES 3.0 viewport + floating
 * Nink panels (Hierarchy, Inspector) rendered above the GL surface.
 *
 * Lifecycle contract of this hotfix:
 *  - entering requests landscape ([EditorOrientation.EDITOR]) and leaving
 *    restores portrait — the activity is never recreated (configChanges),
 *  - all failures (project load, viewport/EGL init, renderer) surface as
 *    localized in-app errors instead of crashing or silently breaking,
 *  - closing clears the editor ViewModel so the save-on-exit flush runs and
 *    re-entering starts from a deterministic reload.
 */
@Composable
fun EditorScreen(
    projectId: String,
    onExit: () -> Unit
) {
    val viewModel: EditorViewModel = viewModel(key = "editor-$projectId")
    viewModel.ensureLoaded(projectId)

    val activity = LocalContext.current.findActivity()

    // Landscape while editing; portrait again for the launcher. Disposable
    // so the restore runs on every path that leaves the editor (exit button,
    // system back gesture, fatal-load error screen).
    DisposableEffect(Unit) {
        activity?.requestedOrientation = EditorOrientation.EDITOR
        onDispose {
            if (activity?.isFinishing == false) {
                activity.requestedOrientation = EditorOrientation.LAUNCHER
            }
        }
    }

    // Save-on-exit flush + back to the launcher on every close path. The
    // editor ViewModel stays in the activity store (cleared on activity death)
    // like the rest of the block; the flush guarantees no scene changes are
    // lost regardless.
    val closeEditor = {
        try {
            viewModel.flushOnExit()
        } catch (t: Throwable) {
            EngineLogger.e("EditorScreen", "Editor teardown failed", t)
        }
        onExit()
    }
    BackHandler { closeEditor() }

    if (viewModel.loadFailed) {
        EditorLoadErrorScreen(
            message = viewModel.loadErrorMessage
                ?: stringResource(R.string.error_generic, "unknown"),
            onBack = closeEditor
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top chrome is painted opaque: status-bar strip + top bar. The
        // viewport area below must stay UNPAINTED so the GL surface (which
        // sits behind the translucent window) remains visible.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(KenkernColors.Background)
                .statusBarsPadding()
        ) {
            EditorTopBar(
                title = viewModel.projectName,
                isLoading = viewModel.isLoading,
                isSaving = viewModel.isSaving,
                canUndo = viewModel.canUndo,
                canRedo = viewModel.canRedo,
                hierarchyVisible = viewModel.hierarchyVisible,
                inspectorVisible = viewModel.inspectorVisible,
                fpsVisible = viewModel.fpsOverlayVisible,
                onUndo = { viewModel.undo() },
                onRedo = { viewModel.redo() },
                onSave = { viewModel.saveNow() },
                onToggleHierarchy = { viewModel.toggleHierarchy() },
                onToggleInspector = { viewModel.toggleInspector() },
                onToggleFps = { viewModel.toggleFpsOverlay() },
                onExit = closeEditor
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            ViewportArea(viewModel)

            // Floating panels above the GL viewport.
            if (viewModel.hierarchyVisible && !viewModel.isLoading) {
                HierarchyPanel(
                    viewModel = viewModel,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 10.dp, top = 12.dp)
                )
            }
            if (viewModel.inspectorVisible && !viewModel.isLoading) {
                InspectorPanel(
                    viewModel = viewModel,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 10.dp, top = 12.dp)
                )
            }

            if (viewModel.fpsOverlayVisible && viewModel.fps > 0) {
                FpsOverlay(
                    fps = viewModel.fps,
                    frameMs = viewModel.frameMs,
                    glSummary = viewModel.glSummary,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                )
            }

            viewModel.renderInitError?.let { error ->
                ViewportErrorBanner(
                    message = stringResource(R.string.viewport_init_error),
                    detail = error,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp)
                )
            }

            if (viewModel.renderInitError == null && !viewModel.hierarchyVisible && !viewModel.inspectorVisible) {
                InputHintChip(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                )
            }

            // Toast (transient) + error banner.
            viewModel.toastMessage?.let { message ->
                ToastBanner(
                    message = message,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                )
            }
            viewModel.errorMessage?.let { message ->
                ErrorBanner(
                    message = message,
                    onDismiss = { viewModel.dismissError() },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                )
            }
        }

        // Bottom system-bar strip stays opaque (window is translucent).
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .background(KenkernColors.Background)
                .navigationBarsPadding()
        )
    }
}

/** Full-screen error state when a project cannot be opened: message + way back. */
@Composable
private fun EditorLoadErrorScreen(message: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KenkernColors.Background)
            .systemBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.error_editor_open_title),
            color = KenkernColors.Danger,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = message,
            color = KenkernColors.TextSecondary,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(containerColor = KenkernColors.Accent),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(R.string.action_back), color = KenkernColors.TextPrimary)
        }
    }
}

@Composable
private fun EditorTopBar(
    title: String,
    isLoading: Boolean,
    isSaving: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    hierarchyVisible: Boolean,
    inspectorVisible: Boolean,
    fpsVisible: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: () -> Unit,
    onToggleHierarchy: () -> Unit,
    onToggleInspector: () -> Unit,
    onToggleFps: () -> Unit,
    onExit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(KenkernColors.Panel),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onExit) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.editor_exit),
                tint = KenkernColors.TextPrimary
            )
        }
        Text(
            text = title,
            color = KenkernColors.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (isLoading || isSaving) {
            CircularProgressIndicator(
                color = KenkernColors.Accent,
                strokeWidth = 2.dp,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(16.dp)
            )
        }
        IconButton(onClick = onUndo, enabled = canUndo) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Undo,
                contentDescription = stringResource(R.string.editor_undo),
                tint = if (canUndo) KenkernColors.TextPrimary else KenkernColors.TextDisabled
            )
        }
        IconButton(onClick = onRedo, enabled = canRedo) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Redo,
                contentDescription = stringResource(R.string.editor_redo),
                tint = if (canRedo) KenkernColors.TextPrimary else KenkernColors.TextDisabled
            )
        }
        IconButton(onClick = onSave) {
            Icon(
                imageVector = Icons.Filled.Save,
                contentDescription = stringResource(R.string.editor_save),
                tint = KenkernColors.TextPrimary
            )
        }
        IconButton(onClick = onToggleHierarchy) {
            Icon(
                imageVector = Icons.Filled.AccountTree,
                contentDescription = stringResource(R.string.editor_toggle_hierarchy),
                tint = if (hierarchyVisible) KenkernColors.Accent else KenkernColors.TextSecondary
            )
        }
        IconButton(onClick = onToggleInspector) {
            Icon(
                imageVector = Icons.Filled.Tune,
                contentDescription = stringResource(R.string.editor_toggle_inspector),
                tint = if (inspectorVisible) KenkernColors.Accent else KenkernColors.TextSecondary
            )
        }
        IconButton(onClick = onToggleFps) {
            Icon(
                imageVector = Icons.Filled.Speed,
                contentDescription = stringResource(R.string.editor_toggle_fps),
                tint = if (fpsVisible) KenkernColors.Accent else KenkernColors.TextSecondary
            )
        }
    }
}

/** Result of the guarded viewport stack creation. */
private sealed interface ViewportSetup {
    data class Ready(val surface: KenkernGLSurfaceView) : ViewportSetup
    data class Unavailable(val detail: String) : ViewportSetup
}

@Composable
private fun ViewportArea(viewModel: EditorViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val deviceInfo = remember {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.deviceConfigurationInfo
    }
    val supportsEs3 = deviceInfo.reqGlEsVersion >= 0x30000

    if (!supportsEs3) {
        val versionText = String.format("0x%04X", deviceInfo.reqGlEsVersion)
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.viewport_gl_unsupported_title),
                    color = KenkernColors.Danger,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.viewport_gl_unsupported_text, versionText),
                    color = KenkernColors.TextSecondary,
                    fontSize = 13.sp
                )
            }
        }
        return
    }

    // Create and validate the whole GL stack once, guarded: EGL pre-flight on
    // the UI thread first, then surface creation. Any failure becomes a UI
    // error state — never an uncaught exception on the GL thread.
    val setup: ViewportSetup = remember {
        try {
            val preflight = EGLPreflight.validate()
            if (!preflight.ok) {
                ViewportSetup.Unavailable(preflight.detail)
            } else {
                val surface = KenkernGLSurfaceView(
                    context,
                    ViewportInputHandler(viewModel.camera, context)
                )
                val renderer = GLRenderer(viewModel.camera, viewModel.frameStats)
                viewModel.attachRenderer(renderer)
                surface.attachRenderer(renderer)
                val setupError = surface.setupError
                if (setupError != null) {
                    ViewportSetup.Unavailable(setupError)
                } else {
                    ViewportSetup.Ready(surface)
                }
            }
        } catch (t: Throwable) {
            EngineLogger.e("Viewport", "Viewport initialization failed", t)
            ViewportSetup.Unavailable(t.message ?: t.javaClass.simpleName)
        }
    }

    when (val current = setup) {
        is ViewportSetup.Unavailable -> {
            // The window is translucent over the viewport area; paint an
            // opaque backdrop so the error state does not show through.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(KenkernColors.Background),
                contentAlignment = Alignment.Center
            ) {
                ViewportErrorBanner(
                    message = stringResource(R.string.viewport_init_error),
                    detail = current.detail,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }

        is ViewportSetup.Ready -> {
            AndroidView(
                factory = { _ -> current.surface },
                modifier = Modifier.fillMaxSize()
            )

            DisposableEffect(lifecycleOwner, current.surface) {
                val surface = current.surface
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_PAUSE -> surface.onPause()
                        Lifecycle.Event.ON_RESUME -> {
                            surface.onResume()
                            viewModel.onRenderResumed()
                        }

                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            viewModel.pollRenderStats()
            delay(250)
        }
    }
}

@Composable
private fun FpsOverlay(fps: Int, frameMs: Float, glSummary: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(KenkernColors.Panel.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
            .border(1.dp, KenkernColors.PanelBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = stringResource(R.string.editor_fps_format, fps, frameMs),
            color = KenkernColors.Success,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        if (glSummary.isNotBlank()) {
            Text(
                text = glSummary,
                color = KenkernColors.TextSecondary,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun InputHintChip(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.viewport_hint),
        color = KenkernColors.TextDisabled,
        fontSize = 11.sp,
        modifier = modifier
            .background(KenkernColors.Panel.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

@Composable
private fun ViewportErrorBanner(message: String, detail: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(KenkernColors.Panel, RoundedCornerShape(12.dp))
            .border(1.dp, KenkernColors.Danger, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text(text = message, color = KenkernColors.Danger, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = detail, color = KenkernColors.TextSecondary, fontSize = 11.sp, maxLines = 6)
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(KenkernColors.Danger.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
            .border(1.dp, KenkernColors.Danger.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = message,
            color = KenkernColors.TextPrimary,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = stringResource(R.string.action_close),
            color = KenkernColors.Danger,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clickable { onDismiss() }
                .padding(6.dp)
        )
    }
}

@Composable
private fun ToastBanner(message: String, modifier: Modifier = Modifier) {
    LaunchedEffect(message) {
        delay(3500)
    }
    Text(
        text = message,
        color = KenkernColors.TextPrimary,
        fontSize = 12.sp,
        modifier = modifier
            .background(KenkernColors.PanelRaised.copy(alpha = 0.95f), RoundedCornerShape(10.dp))
            .border(1.dp, KenkernColors.PanelBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}
