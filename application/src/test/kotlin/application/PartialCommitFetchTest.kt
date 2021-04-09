package application

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import `in`.specmatic.core.CONTRACT_EXTENSION
import `in`.specmatic.core.git.GitCommand

internal class PartialCommitFetchTest {
    @Test
    fun `it should return the commit if no error occurs`() {
        val relativeContractPath = "file.$CONTRACT_EXTENSION"
        val contractPath = "/path/to/file.$CONTRACT_EXTENSION"

        val commitHash = "12345"
        val content = "data"

        val gitRoot = mockk<GitCommand>()
        every { gitRoot.show(commitHash, relativeContractPath) }.returns(content)

        val partial = getFileContentAtSpecifiedCommit(gitRoot)(relativeContractPath)(contractPath)
        val outcome = partial(commitHash)

        assertThat(outcome.errorMessage).isEmpty()
        assertThat(outcome.result).isEqualTo("data")
    }

    @Test
    fun `it should return an error if one occurs`() {
        val relativeContractPath = "file.$CONTRACT_EXTENSION"
        val contractPath = "/path/to/file.$CONTRACT_EXTENSION"

        val commitHash = "12345"

        val gitRoot = mockk<GitCommand>()
        every { gitRoot.show(commitHash, relativeContractPath) } answers { throw Exception("Error") }

        val partial = getFileContentAtSpecifiedCommit(gitRoot)(relativeContractPath)(contractPath)
        val outcome = partial(commitHash)

        assertThat(outcome.errorMessage).isEqualTo("""Could not load 12345:$contractPath because of error:
Error""")
        assertThat(outcome.result).isNull()
    }
}