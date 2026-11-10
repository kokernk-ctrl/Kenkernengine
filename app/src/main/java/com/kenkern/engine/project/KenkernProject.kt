package com.kenkern.engine.project

/**
 * Persistent metadata of a KENKERN project.
 *
 * A project owns a directory on disk:
 *
 * ```
 * &lt;filesDir&gt;/projects/&lt;projectId&gt;/
 *   project.json            <- this metadata
 *   scenes/main.kscene      <- scene data (added by the scene system)
 *   prefabs (kprefab files) <- reusable prefab definitions (added later)
 * ```
 */
data class KenkernProject(
    val id: String,
    val name: String,
    val createdAtMillis: Long,
    val lastModifiedMillis: Long,
    val engineVersion: String,
    val entryScenePath: String
)

/** Lightweight row used by the home screen project list. */
data class ProjectSummary(
    val id: String,
    val name: String,
    val lastModifiedMillis: Long
)
