package application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.Result
import run.qontract.core.git.GitCommand

internal class CompatibleCommandKtTest {
    @Test
    fun `backward compatibility command when the file is not in a git repo`() {
        val fakeReader = object : FileReader {
            override fun read(name: String): String {
                return """
                    Feature: Test
                      Scenario: Test
                        When GET /
                        Then status 200
                """.trimIndent()
            }
        }

        val results = backwardCompatibilityCommand("/Users/fakeuser/newer.qontract", "/Users/fakeuser/older.qontract", fakeReader, GitCommand())
        assertThat(results.successCount).isOne()
        assertThat(results.success()).isTrue()
    }
}