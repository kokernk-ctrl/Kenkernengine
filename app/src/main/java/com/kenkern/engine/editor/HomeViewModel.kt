package com.kenkern.engine.editor

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kenkern.engine.project.ProjectException
import com.kenkern.engine.project.ProjectRepository
import com.kenkern.engine.project.ProjectSummary
import com.kenkern.engine.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * State holder of the KENKERN home screen (project list).
 *
 * Errors are surfaced as user-facing messages through [errorMessage];
 * repository failures are never silently swallowed.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ProjectRepository(application)

    /** `null` while the initial listing is loading. */
    var projects by mutableStateOf<List<ProjectSummary>?>(null)
        private set

    var isCreatingProject by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    init {
        refreshProjects()
    }

    fun refreshProjects() {
        viewModelScope.launch {
            try {
                projects = repository.listProjects()
            } catch (e: Exception) {
                errorMessage = getApplication<Application>().getString(R.string.error_generic, e.message ?: "unknown")
            }
        }
    }

    fun createProject(rawName: String, onCreated: (String) -> Unit) {
        val name = rawName.trim()
        if (name.isEmpty()) {
            errorMessage = getApplication<Application>().getString(R.string.project_name_error)
            return
        }
        viewModelScope.launch {
            isCreatingProject = true
            try {
                val project = repository.createProject(name)
                isCreatingProject = false
                refreshProjects()
                onCreated(project.id)
            } catch (e: Exception) {
                isCreatingProject = false
                errorMessage = getApplication<Application>().getString(R.string.error_project_create, e.message ?: "unknown")
            }
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            try {
                repository.deleteProject(projectId)
                refreshProjects()
            } catch (e: ProjectException) {
                errorMessage = getApplication<Application>().getString(R.string.error_generic, e.message ?: "unknown")
            }
        }
    }

    fun dismissError() {
        errorMessage = null
    }
}
