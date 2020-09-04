package application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.Result
import run.qontract.core.Results
import run.qontract.core.git.GitCommand
import run.qontract.core.git.SystemGit

internal class CompatibleCommandKtTest {
    val trivialContract = """
                    Feature: Test
                      Scenario: Test
                        When GET /
                        Then status 200
                """.trimIndent()

    private val fakeReader = object : FileReader {
        override fun read(path: String): String {
            return trivialContract
        }
    }

    @Test
    fun `compatibility test of file against HEAD when change is backward compatible`() {
        val fakeGit = object : FakeGit() {
            override fun fileIsInGitDir(newerContractPath: String): Boolean = true

            override fun relativeGitPath(newerContractPath: String): Pair<GitCommand, String> {
                assertThat(newerContractPath).isEqualTo("/Users/fakeuser/newer.qontract")
                return Pair(this, "newer.qontract")
            }

            override fun show(treeish: String, relativePath: String): String {
                assertThat(treeish).isEqualTo("HEAD")
                assertThat(relativePath).isEqualTo("newer.qontract")

                return trivialContract
            }
        }

        val results = backwardCompatibleFile("/Users/fakeuser/newer.qontract", fakeReader, fakeGit)
        assertThat(results.successCount).isOne()
        assertThat(results.success()).isTrue()
    }

    @Test
    fun `compatibility test of file against HEAD when change is NOT backward compatible`() {
        val fakeGit = object : FakeGit() {
            override fun fileIsInGitDir(newerContractPath: String): Boolean = true

            override fun relativeGitPath(newerContractPath: String): Pair<GitCommand, String> {
                assertThat(newerContractPath).isEqualTo("/Users/fakeuser/newer.qontract")
                return Pair(this, "newer.qontract")
            }

            override fun show(treeish: String, relativePath: String): String {
                assertThat(treeish).isEqualTo("HEAD")
                assertThat(relativePath).isEqualTo("newer.qontract")

                return """
                    Feature: Test
                      Scenario: Test
                        When POST /
                        Then status 200
                """.trimIndent()
            }
        }

        val results = backwardCompatibleFile("/Users/fakeuser/newer.qontract", fakeReader, fakeGit)
        assertThat(results.successCount).isZero()
        assertThat(results.success()).isFalse()
    }

    @Test
    fun `compatibility test of two commits of a file`() {
        val commitsRequested = mutableListOf<String>()

        val fakeGit = object : FakeGit() {
            override fun fileIsInGitDir(newerContractPath: String): Boolean = true

            override fun relativeGitPath(newerContractPath: String): Pair<GitCommand, String> {
                assertThat(newerContractPath).isEqualTo("/Users/fakeuser/newer.qontract")
                return Pair(this, "newer.qontract")
            }

            override fun show(treeish: String, relativePath: String): String {
                commitsRequested.add(treeish)
                assertThat(relativePath).isEqualTo("newer.qontract")

                return trivialContract
            }
        }

        val results = backwardCompatibleCommit("/Users/fakeuser/newer.qontract", "HEAD", "HEAD^1", fakeGit)

        assertThat(commitsRequested.toList().sorted()).isEqualTo(listOf("HEAD", "HEAD^1"))

        assertThat(results?.successCount).isOne()
        assertThat(results?.success()).isTrue()
    }

    @Test
    fun `compatibility test of two commits of a file when there is only one commit`() {
        val commitsRequested = mutableListOf<String>()

        val fakeGit = object : FakeGit() {
            override fun fileIsInGitDir(newerContractPath: String): Boolean = true

            override fun relativeGitPath(newerContractPath: String): Pair<GitCommand, String> {
                assertThat(newerContractPath).isEqualTo("/Users/fakeuser/newer.qontract")
                return Pair(this, "newer.qontract")
            }

            override fun show(treeish: String, relativePath: String): String {
                commitsRequested.add(treeish)
                assertThat(relativePath).isEqualTo("newer.qontract")

                return when(treeish) {
                    "HEAD" -> trivialContract
                    else -> throw Exception("Only one commit")
                }
            }
        }

        val results = backwardCompatibleCommit("/Users/fakeuser/newer.qontract", "HEAD", "HEAD^1", fakeGit)

        assertThat(results).isNull()
    }

    @Test
    fun `compatibleMessage when newer is backward compatible`() {
        val (exitCode, message) = compatibilityMessage(Results(mutableListOf(Result.Success())))
        assertThat(exitCode).isZero()
        assertThat(message).isEqualTo("The newer contract is backward compatible")
    }

    @Test
    fun `compatibleMessage when newer is not backward compatible`() {
        val (exitCode, message) = compatibilityMessage(Results(mutableListOf(Result.Failure())))
        assertThat(exitCode).isOne()
        assertThat(message).isEqualTo("The newer contract is NOT backward compatible")
    }
}
