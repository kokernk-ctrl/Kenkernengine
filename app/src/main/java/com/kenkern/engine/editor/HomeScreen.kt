package com.kenkern.engine.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kenkern.engine.R
import com.kenkern.engine.core.EngineConfig
import com.kenkern.engine.core.ExitReasonProbe
import com.kenkern.engine.editor.theme.KenkernColors
import com.kenkern.engine.project.ProjectSummary
import java.text.DateFormat
import java.util.Date

/**
 * KENKERN home screen: engine identity + Projects section with an
 * create/open/delete workflow. Opens the editor shell for a project.
 */
@Composable
fun HomeScreen(
    onOpenProject: (String) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val projects = viewModel.projects
    var showCreateDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ProjectSummary?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KenkernColors.Background)
            .systemBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        HeaderSection()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.home_projects_title),
                color = KenkernColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = { showCreateDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = KenkernColors.Accent),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringResource(R.string.home_new_project), color = KenkernColors.TextPrimary)
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when {
                projects == null -> LoadingState()
                projects.isEmpty() -> EmptyProjectsState()
                else -> ProjectList(
                    projects = projects,
                    onOpen = onOpenProject,
                    onDeleteRequest = { pendingDelete = it }
                )
            }
            viewModel.errorMessage?.let { message ->
                ErrorBanner(
                    message = message,
                    onDismiss = { viewModel.dismissError() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                )
            }
            viewModel.lastExit?.takeIf { it.abnormal }?.let { exit ->
                val context = LocalContext.current
                val reason = ExitReasonProbe.reasonLabel(context, exit.reasonCode)
                DiagBanner(
                    message = stringResource(R.string.diag_last_exit_abnormal, reason),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                )
            }
        }

        FooterSection()
    }

    if (showCreateDialog) {
        CreateProjectDialog(
            isCreating = viewModel.isCreatingProject,
            onConfirm = { name ->
                viewModel.createProject(name) { projectId ->
                    showCreateDialog = false
                    onOpenProject(projectId)
                }
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    pendingDelete?.let { project ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.home_delete_confirm_title)) },
            text = { Text(stringResource(R.string.home_delete_confirm_text, project.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteProject(project.id)
                        pendingDelete = null
                    }
                ) {
                    Text(stringResource(R.string.home_delete), color = KenkernColors.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun HeaderSection() {
    Column(modifier = Modifier.padding(top = 40.dp)) {
        Text(
            text = stringResource(R.string.app_name),
            color = KenkernColors.TextPrimary,
            fontSize = 38.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 8.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .width(64.dp)
                .height(3.dp)
                .background(KenkernColors.Accent, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.home_tagline),
            color = KenkernColors.TextSecondary,
            fontSize = 14.sp,
            letterSpacing = 1.5.sp
        )
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = KenkernColors.Accent)
            Spacer(modifier = Modifier.height(12.dp))
            Text(stringResource(R.string.home_loading), color = KenkernColors.TextSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun EmptyProjectsState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.home_empty_title),
                color = KenkernColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.home_empty_hint),
                color = KenkernColors.TextSecondary,
                fontSize = 13.sp,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProjectList(
    projects: List<ProjectSummary>,
    onOpen: (String) -> Unit,
    onDeleteRequest: (ProjectSummary) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(projects, key = { it.id }) { project ->
            ProjectCard(
                project = project,
                onOpen = onOpen,
                onDeleteRequest = onDeleteRequest
            )
        }
    }
}

@Composable
private fun ProjectCard(
    project: ProjectSummary,
    onOpen: (String) -> Unit,
    onDeleteRequest: (ProjectSummary) -> Unit
) {
    val dateText = remember(project.lastModifiedMillis) {
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(project.lastModifiedMillis))
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, KenkernColors.PanelBorder, RoundedCornerShape(14.dp))
            .clickable { onOpen(project.id) },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = KenkernColors.Panel)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(KenkernColors.Accent, RoundedCornerShape(5.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.name,
                    color = KenkernColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.home_last_modified_format, dateText),
                    color = KenkernColors.TextSecondary,
                    fontSize = 12.sp
                )
            }
            IconButton(onClick = { onDeleteRequest(project) }) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.home_delete),
                    tint = KenkernColors.TextSecondary
                )
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(KenkernColors.Danger.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
            .border(1.dp, KenkernColors.Danger.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = message,
            color = KenkernColors.TextPrimary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = stringResource(R.string.action_close),
            color = KenkernColors.Danger,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clickable { onDismiss() }
                .padding(6.dp)
        )
    }
}

/**
 * Forensic diagnostic banner (HOTFIX investigation): informs the user that
 * the previous process run ended abnormally (crash / ANR / kill) and where
 * the on-device report is stored. Informational only — it never blocks or
 * degrades any feature.
 */
@Composable
private fun DiagBanner(message: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth(0.92f)
            .background(KenkernColors.Panel.copy(alpha = 0.92f), RoundedCornerShape(10.dp))
            .border(1.dp, KenkernColors.PanelBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = message,
            color = KenkernColors.TextSecondary,
            fontSize = 11.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun FooterSection() {
    Text(
        text = stringResource(R.string.home_version_format, EngineConfig.VERSION),
        color = KenkernColors.TextDisabled,
        fontSize = 11.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    )
}

@Composable
private fun CreateProjectDialog(
    isCreating: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!isCreating) onDismiss() },
        title = { Text(stringResource(R.string.home_new_project)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = false
                    },
                    label = { Text(stringResource(R.string.project_name_label)) },
                    placeholder = { Text(stringResource(R.string.project_name_hint)) },
                    singleLine = true,
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text(stringResource(R.string.project_name_error)) }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.trim().isEmpty()) {
                        nameError = true
                    } else {
                        onConfirm(name)
                    }
                },
                enabled = !isCreating,
                colors = ButtonDefaults.buttonColors(containerColor = KenkernColors.Accent)
            ) {
                Text(stringResource(R.string.action_create))
            }
        },
        dismissButton = {
            TextButton(onClick = { if (!isCreating) onDismiss() }) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
