package application

import application.backwardCompatibility.BackwardCompatibilityCheckCommandV2
import io.mockk.every
import io.mockk.spyk
import io.specmatic.core.git.SystemGit
import io.specmatic.core.utilities.SystemExit
import io.specmatic.core.utilities.SystemExitException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

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
            assertEquals(setOf(File("file1.yaml").canonicalPath), result)
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
            assertEquals(
                setOf("file1.yaml", "schema_file2.yaml", "file2.yaml").map { File(it).canonicalPath }.toSet(), result
            )
        }

        @Test
        fun `getSpecsReferringTo should not hang if there is a circular dependency`() {
            val command = spyk<BackwardCompatibilityCheckCommandV2>()
            every { command.allSpecFiles() } returns listOf(
                File("a.yaml").apply { referTo("b.yaml") },
                File("b.yaml").apply { referTo("c.yaml") },
                File("c.yaml").apply { referTo("a.yaml") }
            )

            assertThat(command.getSpecsReferringTo(setOf("a.yaml"))).isEqualTo(setOf("b.yaml", "c.yaml").map { File(it).canonicalPath }.toSet())
            assertThat(command.getSpecsReferringTo(setOf("b.yaml"))).isEqualTo(setOf("c.yaml", "a.yaml").map { File(it).canonicalPath }.toSet())
            assertThat(command.getSpecsReferringTo(setOf("c.yaml"))).isEqualTo(setOf("a.yaml", "b.yaml").map { File(it).canonicalPath }.toSet())
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

    @Nested
    inner class ExternalExampleTests {

        @Test
        fun `should catch when external example files are modified and run backward compatibility check on respective api spec`() {
            val oasDir = File("src/test/resources/specifications/spec_with_examples")
            oasDir.copyRecursively(remoteDir); oasDir.copyRecursively(tempDir)
            commitAndPush(tempDir, "Initial commit")

            val exampleFile = tempDir.resolve("api_examples").resolve("example.json")
            exampleFile.writeText(exampleFile.readText().replace("john", "jane"))

            val (stdOut, exception) = captureStandardOutput {
                assertThrows<SystemExitException> {
                    SystemExit.throwOnExit {
                        BackwardCompatibilityCheckCommandV2().apply { repoDir = tempDir.canonicalPath }.call()
                    }
                }
            }

            assertThat(exception.code).isEqualTo(0)
            assertThat(stdOut).containsIgnoringWhitespaces("""
            - Specs that have changed: 
            1. ${exampleFile.path}
            - Specs whose externalised examples were changed:
            1. ${tempDir.resolve("api.yaml").path}
            """.trimIndent()).containsIgnoringWhitespaces("""
            Files checked: 2 (Passed: 2, Failed: 0)
            """.trimIndent())
        }

        @ParameterizedTest
        @CsvSource(
            "/api/api_examples/example.json, /api/api_examples",
            "/api/api_examples/product/example.json, /api/api_examples",
            "/api_tests/example.json, /api_tests",
            "/api/api_config/config.json, ",
            "/example.json, "
        )
        fun `should be able to properly resolve examples dir when by walking up the example file path`(exampleFile: String, expectedDir: String?) {
            val exampleDir = BackwardCompatibilityCheckCommandV2().getParentExamplesDirectory(Paths.get(exampleFile))
            assertThat(exampleDir).isEqualTo(expectedDir?.let(Paths::get))
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

    private fun commitAndPush(repoDir: File, commitMessage: String) {
        ProcessBuilder("git", "add", ".").directory(repoDir).inheritIO().start().waitFor()
        ProcessBuilder("git", "commit", "-m", commitMessage).directory(repoDir).inheritIO().start().waitFor()
        ProcessBuilder("git", "push", "origin", "master").directory(repoDir).inheritIO().start().waitFor()
    }
}