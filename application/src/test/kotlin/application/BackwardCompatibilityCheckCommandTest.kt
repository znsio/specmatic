package application

import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
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
        val command = spyk<BackwardCompatibilityCheckCommand>()
        every { command.allOpenApiSpecFiles() } returns listOf(
            File("file1.yaml").apply { writeText("content1") },
            File("file2.yaml").apply { writeText("content2") }
        )
        val result = command.filesReferringToChangedSchemaFiles(setOf("file3.yaml"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `filesReferringToChangedSchemaFiles returns set of files that refer to changed schema files`() {
        val command = spyk<BackwardCompatibilityCheckCommand>()
        every { command.allOpenApiSpecFiles() } returns listOf(
            File("file1.yaml").apply { writeText("file3.yaml") },
            File("file2.yaml").apply { writeText("file4.yaml") }
        )
        val result = command.filesReferringToChangedSchemaFiles(setOf("file3.yaml"))
        assertEquals(setOf("file1.yaml"), result)
    }

    @Test
    fun `filesReferringToChangedSchemaFiles returns set of files which are referring to a changed schema that is one level down`() {
        val command = spyk<BackwardCompatibilityCheckCommand>()
        every { command.allOpenApiSpecFiles() } returns listOf(
            File("file1.yaml").apply { referTo("schema_file1.yaml") },
            File("schema_file2.yaml").apply { referTo("schema_file1.yaml") }, // schema within a schema
            File("file2.yaml").apply { referTo("schema_file2.yaml") }
        )
        val result = command.filesReferringToChangedSchemaFiles(setOf("schema_file1.yaml"))
        assertEquals(setOf("file1.yaml", "file2.yaml"), result)
    }

    @AfterEach
    fun `cleanup files`() {
        listOf("file1.yaml", "file2.yaml", "file3.yaml", "file4.yaml", "schema_file1.yaml", "schema_file2.yaml").forEach {
            File(it).delete()
        }
    }

    private fun File.referTo(schemaFileName: String) {
       val specContent = """
           openapi: 3.1.0  # OpenAPI version specified here
           info:
             title: My API
             version: 1.0.0
           components:
             schemas:
               User:
                 ${"$"}ref: '#/components/schemas/$schemaFileName' 
       """.trimIndent()
        this.writeText(specContent)
    }
}