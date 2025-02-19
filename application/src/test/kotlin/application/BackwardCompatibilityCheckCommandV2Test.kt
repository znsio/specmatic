package application

import application.backwardCompatibility.BackwardCompatibilityCheckCommandV2
import io.mockk.every
import io.mockk.spyk
import io.specmatic.core.git.SystemGit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class BackwardCompatibilityCheckCommandV2Test {
    private lateinit var tempDir: File
    private lateinit var remoteDir: File

    @BeforeEach
    fun setup() {
        tempDir = Files.createTempDirectory("git-local").toFile()
        tempDir.deleteOnExit()

        remoteDir = Files.createTempDirectory("git-remote").toFile()
        remoteDir.deleteOnExit()

        ProcessBuilder("git", "init", "--bare")
            .directory(remoteDir)
            .inheritIO()
            .start()
            .waitFor()

        ProcessBuilder("git", "init")
            .directory(tempDir)
            .inheritIO()
            .start()
            .waitFor()

        ProcessBuilder("git", "config", "--local", "user.name", "developer")
            .directory(tempDir)
            .inheritIO()
            .start()
            .waitFor()

        ProcessBuilder("git", "config", "--local", "user.email", "developer@example.com")
            .directory(tempDir)
            .inheritIO()
            .start()
            .waitFor()

        ProcessBuilder("git", "remote", "add", "origin", remoteDir.absolutePath)
            .directory(tempDir)
            .inheritIO()
            .start()
            .waitFor()
    }

    @Nested
    inner class GetSpecsReferringToTests {
        @Test
        fun `getSpecsReferringTo returns empty set when input is empty`() {
            val command = BackwardCompatibilityCheckCommandV2()
            val result = command.getSpecsReferringTo(emptySet())
            assertTrue(result.isEmpty())
        }

        @Test
        fun `getSpecsReferringTo returns empty set when no files refer to changed schema files`() {
            val command = spyk<BackwardCompatibilityCheckCommandV2>()
            every { command.allSpecFiles() } returns listOf(
                File("file1.yaml").apply { writeText("content1") },
                File("file2.yaml").apply { writeText("content2") }
            )
            val result = command.getSpecsReferringTo(setOf("file3.yaml"))
            assertTrue(result.isEmpty())
        }

        @Test
        fun `getSpecsReferringTo returns set of files that refer to changed schema files`() {
            val command = spyk<BackwardCompatibilityCheckCommandV2>()
            every { command.allSpecFiles() } returns listOf(
                File("file1.yaml").apply { writeText("file3.yaml") },
                File("file2.yaml").apply { writeText("file4.yaml") }
            )
            val result = command.getSpecsReferringTo(setOf("file3.yaml"))
            assertEquals(setOf("file1.yaml"), result)
        }

        @Test
        fun `getSpecsReferringTo returns set of files which are referring to a changed schema that is one level down`() {
            val command = spyk<BackwardCompatibilityCheckCommandV2>()
            every { command.allSpecFiles() } returns listOf(
                File("file1.yaml").apply { referTo("schema_file1.yaml") },
                File("schema_file2.yaml").apply { referTo("schema_file1.yaml") }, // schema within a schema
                File("file2.yaml").apply { referTo("schema_file2.yaml") }
            )
            val result = command.getSpecsReferringTo(setOf("schema_file1.yaml"))
            assertEquals(setOf("file1.yaml", "schema_file2.yaml", "file2.yaml"), result)
        }

        @Test
        fun `getSpecsReferringTo should not hang if there is a circular dependency`() {
            val command = spyk<BackwardCompatibilityCheckCommandV2>()
            every { command.allSpecFiles() } returns listOf(
                File("a.yaml").apply { referTo("b.yaml") },
                File("b.yaml").apply { referTo("c.yaml") },
                File("c.yaml").apply { referTo("a.yaml") }
            )

            assertThat(command.getSpecsReferringTo(setOf("a.yaml"))).isEqualTo(setOf("b.yaml", "c.yaml"))
            assertThat(command.getSpecsReferringTo(setOf("b.yaml"))).isEqualTo(setOf("c.yaml", "a.yaml"))
            assertThat(command.getSpecsReferringTo(setOf("c.yaml"))).isEqualTo(setOf("a.yaml", "b.yaml"))
        }
    }

    @Nested
    inner class SystemGitTestsSpecificToBackwardCompatibility {
        @Test
        fun `getFilesChangedInCurrentBranch returns the uncommitted, unstaged changed file`() {
            File(tempDir, "file1.txt").writeText("File 1 content")
            ProcessBuilder("git", "add", "file1.txt")
                .directory(tempDir)
                .inheritIO()
                .start()
                .waitFor()
            ProcessBuilder("git", "commit", "-m", "Add file1")
                .directory(tempDir)
                .inheritIO()
                .start()
                .waitFor()
            // Push the committed changes to the remote repository
            ProcessBuilder("git", "push", "origin", "main")
                .directory(tempDir)
                .inheritIO()
                .start()
                .waitFor()


            val uncommittedFile = File(tempDir, "file1.txt")
            uncommittedFile.writeText("File 1 changed content")

            val gitCommand = SystemGit(tempDir.absolutePath)
            val result = gitCommand.getFilesChangedInCurrentBranch(
                gitCommand.currentRemoteBranch()
            ).map { it.substringAfterLast(File.separator) }

            assert(result.contains("file1.txt"))
        }

        @Test
        fun `getFilesChangedInCurrentBranch returns the uncommitted, staged changed file`() {
            File(tempDir, "file1.txt").writeText("File 1 content")
            ProcessBuilder("git", "add", "file1.txt")
                .directory(tempDir)
                .inheritIO()
                .start()
                .waitFor()
            ProcessBuilder("git", "commit", "-m", "Add file1")
                .directory(tempDir)
                .inheritIO()
                .start()
                .waitFor()
            // Push the committed changes to the remote repository
            ProcessBuilder("git", "push", "origin", "main")
                .directory(tempDir)
                .inheritIO()
                .start()
                .waitFor()


            val uncommittedFile = File(tempDir, "file1.txt")
            uncommittedFile.writeText("File 1 changed content")
            ProcessBuilder("git", "add", "file1.txt")
                .directory(tempDir)
                .inheritIO()
                .start()
                .waitFor()

            val gitCommand = SystemGit(tempDir.absolutePath)
            val result = gitCommand.getFilesChangedInCurrentBranch(
                gitCommand.currentRemoteBranch()
            ).map { it.substringAfterLast(File.separator) }

            assert(result.contains("file1.txt"))
        }
    }

    @AfterEach
    fun `cleanup files`() {
        listOf(
            "file1.yaml",
            "file2.yaml",
            "file3.yaml",
            "file4.yaml",
            "schema_file1.yaml",
            "schema_file2.yaml",
            "a.yaml",
            "b.yaml",
            "c.yaml"
        ).forEach {
            File(it).delete()
        }
        tempDir.deleteRecursively()
        remoteDir.deleteRecursively()
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