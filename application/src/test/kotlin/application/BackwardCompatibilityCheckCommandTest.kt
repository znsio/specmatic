package application

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

class BackwardCompatibilityCheckCommandTest {

    @Test
    fun `filesReferringToChangedSchemaFiles returns empty set when input is empty`() {
        val command = BackwardCompatibilityCheckCommand(mockk())
        val result = command.filesReferringToChangedSchemaFiles(emptySet())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `filesReferringToChangedSchemaFiles returns empty set when no files refer to changed schema files`() {
        val command = BackwardCompatibilityCheckCommand(mockk(relaxed = true))
        every { command.allOpenApiSpecFiles() } returns listOf(
            File("file1.yaml").apply { writeText("content1") },
            File("file2.yaml").apply { writeText("content2") }
        )
        val result = command.filesReferringToChangedSchemaFiles(setOf("file3.yaml"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `filesReferringToChangedSchemaFiles returns set of files that refer to changed schema files`() {
        val command = mockk<BackwardCompatibilityCheckCommand>(relaxed = true)
        every { command.allOpenApiSpecFiles() } returns listOf(
            File("file1.yaml").apply { writeText("file3.yaml") },
            File("file2.yaml").apply { writeText("file4.yaml") }
        )
        val result = command.filesReferringToChangedSchemaFiles(setOf("file3.yaml"))
        assertEquals(setOf("file1.yaml"), result)
    }
}