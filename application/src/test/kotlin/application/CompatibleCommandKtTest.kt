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

    val fakeReader = object : FileReader {
        override fun read(name: String): String {
            return trivialContract
        }
    }

    @Test
    fun `backward compatibility command when the file is in a git repo`() {
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

        val results = backwardCompatibilityCommand("/Users/fakeuser/newer.qontract", null, fakeReader, fakeGit)
        assertThat(results.successCount).isOne()
        assertThat(results.success()).isTrue()
    }

    @Test
    fun `backward compatibility command when the file is not in a git repo`() {
        val results = backwardCompatibilityCommand("/Users/fakeuser/newer.qontract", "/Users/fakeuser/older.qontract", fakeReader, SystemGit())
        assertThat(results.successCount).isOne()
        assertThat(results.success()).isTrue()
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
