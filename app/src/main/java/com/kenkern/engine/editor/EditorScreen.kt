package com.kenkern.engine.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kenkern.engine.R
import com.kenkern.engine.editor.theme.KenkernColors
import com.kenkern.engine.project.ProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Phase 0 editor shell.
 *
 * Provides the editor chrome (top bar, exit flow) and reserves the viewport
 * region. The OpenGL ES 3.0 viewport is integrated in Phase 1 and the Nink
 * panels in Phase 2 — this shell never pretends to render.
 */
@Composable
fun EditorScreen(
    projectId: String,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    var projectName by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<Boolean>(false) }

    LaunchedEffect(projectId) {
        val repository = ProjectRepository(context)
        try {
            val project = withContext(Dispatchers.IO) { repository.loadProject(projectId) }
            projectName = project.name
        } catch (e: Exception) {
            loadError = true
        }
    }

    BackHandler { onExit() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KenkernColors.Background)
            .systemBarsPadding()
    ) {
        EditorTopBar(
            title = projectName ?: stringResource(R.string.app_name),
            onExit = onExit
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            when {
                loadError -> Text(
                    text = stringResource(R.string.error_project_load, projectId),
                    color = KenkernColors.Danger,
                    fontSize = 14.sp
                )
                projectName == null -> CircularProgressIndicator(color = KenkernColors.Accent)
                else -> Text(
                    text = "3D viewport placeholder — rendering foundation lands in Phase 1",
                    color = KenkernColors.TextDisabled,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
fun EditorTopBar(
    title: String,
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
    }
}
