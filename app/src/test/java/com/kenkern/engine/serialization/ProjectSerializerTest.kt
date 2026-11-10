package com.kenkern.engine.serialization

import com.kenkern.engine.project.KenkernProject
import com.kenkern.engine.project.ProjectFormatException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectSerializerTest {

    private fun sampleProject() = KenkernProject(
        id = "12345678-90ab-cdef-1234-567890abcdef",
        name = "My Game",
        createdAtMillis = 1_700_000_000_000,
        lastModifiedMillis = 1_700_000_500_000,
        engineVersion = "0.1.0",
        entryScenePath = "scenes/main.kscene"
    )

    @Test
    fun `project roundtrip preserves all fields`() {
        val project = sampleProject()
        val restored = ProjectSerializer.fromJson(ProjectSerializer.toJsonString(project))
        assertEquals(project, restored)
    }

    @Test
    fun `malformed json raises ProjectFormatException`() {
        var thrown = false
        try {
            ProjectSerializer.fromJson("not json at all")
        } catch (e: ProjectFormatException) {
            thrown = true
        }
        assertTrue(thrown)
    }

    @Test
    fun `wrong format id is rejected`() {
        val json = """{"format":"unreal-project","version":1,"id":"x"}"""
        var thrown = false
        try {
            ProjectSerializer.fromJson(json)
        } catch (e: ProjectFormatException) {
            thrown = true
        }
        assertTrue(thrown)
    }

    @Test
    fun `wrong version is rejected`() {
        val json = """{"format":"kenkern-project","version":99,"id":"x"}"""
        var thrown = false
        try {
            ProjectSerializer.fromJson(json)
        } catch (e: ProjectFormatException) {
            thrown = true
        }
        assertTrue(thrown)
    }

    @Test
    fun `missing id is rejected`() {
        val json = """{"format":"kenkern-project","version":1,"name":"NoId"}"""
        var thrown = false
        try {
            ProjectSerializer.fromJson(json)
        } catch (e: ProjectFormatException) {
            thrown = true
        }
        assertTrue(thrown)
    }

    @Test
    fun `blank name falls back to Untitled`() {
        val json = """{"format":"kenkern-project","version":1,"id":"abc","name":""}"""
        val restored = ProjectSerializer.fromJson(json)
        assertEquals("Untitled", restored.name)
    }
}
