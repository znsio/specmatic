package application

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import `in`.specmatic.core.CONTRACT_EXTENSION
import `in`.specmatic.core.Result
import `in`.specmatic.core.Results
import `in`.specmatic.core.git.GitCommand

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [SpecmaticApplication::class, CompatibleCommand::class])
internal class CompatibleCommandKtTest {
    val trivialContract = """
                    Feature: Test
                      Scenario: Test
                        When GET /
                        Then status 200
                """.trimIndent()

    @MockkBean
    lateinit var fileOperations: FileOperations

    @MockkBean
    lateinit var gitCommand: GitCommand

    @BeforeEach
    fun testSetup() {
        every { fileOperations.read("/Users/fakeuser/newer.$CONTRACT_EXTENSION") }.returns(trivialContract)
    }

    @Test
    fun `error when contract file path is not in git repo`() {
        every { gitCommand.fileIsInGitDir(any()) }.returns(false)
        val outcome = getOlderFeature("/not/in/git.$CONTRACT_EXTENSION", gitCommand)
        assertThat(outcome.result).isNull()
        assertThat(outcome.errorMessage).isEqualTo("Older contract file must be provided, or the file must be in a git directory")
    }

    @Test
    fun `compatibility test of file against HEAD when change is backward compatible`() {
        val fakeGit = object : FakeGit() {
            override fun fileIsInGitDir(newerContractPath: String): Boolean = true

            override fun relativeGitPath(newerContractPath: String): Pair<GitCommand, String> {
                assertThat(newerContractPath).isEqualTo("/Users/fakeuser/newer.$CONTRACT_EXTENSION")
                return Pair(this, "newer.$CONTRACT_EXTENSION")
            }

            override fun show(treeish: String, relativePath: String): String {
                assertThat(treeish).isEqualTo("HEAD")
                assertThat(relativePath).isEqualTo("newer.$CONTRACT_EXTENSION")

                return trivialContract
            }
        }

        val outcome = backwardCompatibleFile(
            "/Users/fakeuser/newer.$CONTRACT_EXTENSION",
            fileOperations,
            fakeGit
        )
        assertThat(outcome.result?.successCount).isOne()
        assertThat(outcome.result?.success()).isTrue()
    }

    @Test
    fun `compatibility test of file against HEAD when change is NOT backward compatible`() {
        val fakeGit = object : FakeGit() {
            override fun fileIsInGitDir(newerContractPath: String): Boolean = true

            override fun relativeGitPath(newerContractPath: String): Pair<GitCommand, String> {
                assertThat(newerContractPath).isEqualTo("/Users/fakeuser/newer.$CONTRACT_EXTENSION")
                return Pair(this, "newer.$CONTRACT_EXTENSION")
            }

            override fun show(treeish: String, relativePath: String): String {
                assertThat(treeish).isEqualTo("HEAD")
                assertThat(relativePath).isEqualTo("newer.$CONTRACT_EXTENSION")

                return """
                    Feature: Test
                      Scenario: Test
                        When POST /
                        Then status 200
                """.trimIndent()
            }
        }

        val outcome = backwardCompatibleFile(
            "/Users/fakeuser/newer.$CONTRACT_EXTENSION",
            fileOperations,
            fakeGit
        )
        assertThat(outcome.result?.successCount).isZero()
        assertThat(outcome.result?.success()).isFalse()
    }

    @Test
    fun `compatibility test of two commits of a file`() {
        val commitsRequested = mutableListOf<String>()

        val fakeGit = object : FakeGit() {
            override fun fileIsInGitDir(newerContractPath: String): Boolean = true

            override fun relativeGitPath(newerContractPath: String): Pair<GitCommand, String> {
                assertThat(newerContractPath).isEqualTo("/Users/fakeuser/newer.$CONTRACT_EXTENSION")
                return Pair(this, "newer.$CONTRACT_EXTENSION")
            }

            override fun show(treeish: String, relativePath: String): String {
                commitsRequested.add(treeish)
                assertThat(relativePath).isEqualTo("newer.$CONTRACT_EXTENSION")

                return trivialContract
            }
        }

        val outcome = backwardCompatibleCommit(
            "/Users/fakeuser/newer.$CONTRACT_EXTENSION",
            "HEAD",
            "HEAD^1",
            fakeGit
        )

        assertThat(commitsRequested.toList().sorted()).isEqualTo(listOf("HEAD", "HEAD^1"))

        assertThat(outcome.result?.successCount).isOne()
        assertThat(outcome.result?.success()).isTrue()
    }

    @Test
    fun `compatibility test of two commits of a file when there is only one commit`() {
        val commitsRequested = mutableListOf<String>()

        val fakeGit = object : FakeGit() {
            override fun fileIsInGitDir(newerContractPath: String): Boolean = true

            override fun relativeGitPath(newerContractPath: String): Pair<GitCommand, String> {
                assertThat(newerContractPath).isEqualTo("/Users/fakeuser/newer.$CONTRACT_EXTENSION")
                return Pair(this, "newer.$CONTRACT_EXTENSION")
            }

            override fun show(treeish: String, relativePath: String): String {
                commitsRequested.add(treeish)
                assertThat(relativePath).isEqualTo("newer.$CONTRACT_EXTENSION")

                return when(treeish) {
                    "HEAD" -> trivialContract
                    else -> throw Exception("Only one commit")
                }
            }
        }

        val outcome = backwardCompatibleCommit(
            "/Users/fakeuser/newer.$CONTRACT_EXTENSION",
            "HEAD",
            "HEAD^1",
            fakeGit
        )

        assertThat(outcome.errorMessage).isEqualTo("""Could not load HEAD^1:/Users/fakeuser/newer.$CONTRACT_EXTENSION because of error:
Only one commit
        """.trimMargin())
        assertThat(outcome.result).isNull()
    }

    @Test
    fun `compatibleMessage when newer is backward compatible`() {
        val (exitCode, message) = compatibilityMessage(Outcome(Results(mutableListOf(Result.Success()))))
        assertThat(exitCode).isZero()
        assertThat(message).isEqualTo("The newer contract is backward compatible")
    }

    @Test
    fun `compatibleMessage when newer is not backward compatible`() {
        val (exitCode, message) = compatibilityMessage(Outcome(Results(mutableListOf(Result.Failure()))))
        assertThat(exitCode).isOne()
        assertThat(message).isEqualTo("""Tests run: 1, Passed: 0, Failed: 1

The newer contract is NOT backward compatible""")
    }

    @Test
    fun `compatibleMessage when an error hindered the outcome`() {
        val (exitCode, message) = compatibilityMessage(Outcome(null, "ERROR"))
        assertThat(exitCode).isOne()
        assertThat(message).isEqualTo("""ERROR""")
    }

    @Test
    fun `compatibleMessage when an error hindered the outcome but we are passing anyway`() {
        val (exitCode, message) = compatibilityMessage(Outcome(Results(mutableListOf(Result.Success()))))
        assertThat(exitCode).isZero()
        assertThat(message).isEqualTo("The newer contract is backward compatible")
    }

    @Test
    fun `compatibleMessage when an error hindered the outcome but we are passing anyway with a specific error message`() {
        val (exitCode, message) = compatibilityMessage(Outcome(Results(mutableListOf(Result.Success())), "REASON"))
        assertThat(exitCode).isZero()
        assertThat(message).isEqualTo("REASON")
    }
}
