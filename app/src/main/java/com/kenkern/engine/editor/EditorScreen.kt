package com.kenkern.engine.editor

import android.app.ActivityManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Speed
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
import com.kenkern.engine.editor.theme.KenkernColors
import com.kenkern.engine.input.ViewportInputHandler
import com.kenkern.engine.rendering.GLCapabilities
import com.kenkern.engine.rendering.GLRenderer
import com.kenkern.engine.rendering.KenkernGLSurfaceView
import kotlinx.coroutines.delay

/**
 * KENKERN editor screen (Phase 1): editor chrome + live OpenGL ES 3.0
 * viewport with orbit camera, reference grid and FPS diagnostics.
 * Floating Nink panels arrive with Phase 2.
 */
@Composable
fun EditorScreen(
    projectId: String,
    onExit: () -> Unit
) {
    val viewModel: EditorViewModel = viewModel(key = "editor-$projectId")
    viewModel.ensureLoaded(projectId)

    BackHandler { onExit() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KenkernColors.Background)
            .systemBarsPadding()
    ) {
        EditorTopBar(
            title = viewModel.projectName,
            isLoading = viewModel.isLoading,
            fpsVisible = viewModel.fpsOverlayVisible,
            onToggleFps = { viewModel.toggleFpsOverlay() },
            onExit = onExit
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            ViewportArea(viewModel)

            if (viewModel.fpsOverlayVisible && viewModel.fps > 0) {
                FpsOverlay(
                    fps = viewModel.fps,
                    frameMs = viewModel.frameMs,
                    glSummary = viewModel.glSummary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
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

            if (viewModel.renderInitError == null) {
                InputHintChip(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                )
            }
        }
    }
}

@Composable
fun EditorTopBar(
    title: String,
    isLoading: Boolean,
    fpsVisible: Boolean,
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
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = title,
            color = KenkernColors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (isLoading) {
            CircularProgressIndicator(
                color = KenkernColors.Accent,
                strokeWidth = 2.dp,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(18.dp)
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

@Composable
private fun ViewportArea(viewModel: EditorViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Device capability pre-check: the editor reports unsupported GPUs
    // instead of crashing inside EGL.
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

    var glView by remember { mutableStateOf<KenkernGLSurfaceView?>(null) }

    AndroidView(
        factory = { ctx ->
            KenkernGLSurfaceView(ctx, ViewportInputHandler(viewModel.camera, ctx)).apply {
                val renderer = GLRenderer(viewModel.camera, viewModel.frameStats)
                viewModel.attachRenderer(renderer)
                attachRenderer(renderer)
            }.also { created ->
                glView = created
            }
        },
        modifier = Modifier.fillMaxSize()
    )

    // Render loop follows the Android lifecycle: pause stops the GL thread,
    // resume restarts it; context recreation is handled inside the renderer.
    DisposableEffect(lifecycleOwner, glView) {
        val surface = glView
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> surface?.onPause()
                Lifecycle.Event.ON_RESUME -> {
                    surface?.onResume()
                    viewModel.onRenderResumed()
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Low-rate statistics polling for the FPS overlay.
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
