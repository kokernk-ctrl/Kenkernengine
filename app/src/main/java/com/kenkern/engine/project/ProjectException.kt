package com.kenkern.engine.project

/** Base class for project persistence failures. Never silently ignored. */
sealed class ProjectException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Raised when project metadata exists but cannot be parsed/validated. */
class ProjectFormatException(message: String, cause: Throwable? = null) : ProjectException(message, cause)

/** Raised when the requested project does not exist on disk. */
class ProjectNotFoundException(message: String) : ProjectException(message)

/** Raised when a project cannot be loaded (missing files, IO errors...). */
class ProjectLoadException(message: String, cause: Throwable? = null) : ProjectException(message, cause)

/** Raised when writing project data fails. */
class ProjectSaveException(message: String, cause: Throwable? = null) : ProjectException(message, cause)
