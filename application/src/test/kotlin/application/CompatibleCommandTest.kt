package application

import com.ninjasquad.springmockk.MockkBean
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import picocli.CommandLine
import io.specmatic.core.CONTRACT_EXTENSION
import io.specmatic.core.git.GitCommand
import io.specmatic.core.git.NonZeroExitError
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import java.io.FileNotFoundException

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [SpecmaticApplication::class, CompatibleCommand::class])
internal class CompatibleCommandTest {
    @Autowired
    lateinit var factory: CommandLine.IFactory

    @Autowired
    lateinit var compatibleCommand: CompatibleCommand

    @MockkBean
    lateinit var gitCommand: GitCommand

    @MockkBean
    lateinit var fileOperations: FileOperations

    private val relativeContractPath = "api_1.$CONTRACT_EXTENSION"
    private val contractPath = "/path/to/$relativeContractPath"

    @Test
    fun `should check compatibility between file in working tree and HEAD`() {
        val trivialContract = """
            Feature: Random number
              Scenario: Random number
                When GET /
                Then status 200
                And response-body (number)
        """.trimIndent()

        setupHEADCompatibilityCheck(trivialContract, trivialContract)

        every { fileOperations.isFile(any()) }.returns(true)
        every { fileOperations.isDirectory(any()) }.returns(false)
        val exitCode = CommandLine(compatibleCommand, factory).execute("git", "file", contractPath)
        assertThat(exitCode).isZero()
    }

    @Test
    fun `should fail check for incompatible change between working tree and HEAD`() {
        val oldContract = """
            Feature: Random number
              Scenario: Random number
                When GET /
                Then status 200
                And response-body (number)
        """.trimIndent()

        val newContract = """
            Feature: Random number
              Scenario: Random number
                When GET /random
                Then status 200
                And response-body (number)
        """.trimIndent()

        setupHEADCompatibilityCheck(oldContract, newContract)

        val exitCode = CommandLine(compatibleCommand, factory).execute("git", "file", contractPath)
        assertThat(exitCode).isOne()
    }

    @BeforeEach
    fun beforeEach() {
        clearAllMocks()
//        val contractGitCommand = mockk<GitCommand>()
//        clearMocks(contractGitCommand)
//        clearMocks(fileOperations)
    }

    private fun setupHEADCompatibilityCheck(oldContract: String, newContract: String) {
        val contractGitCommand = mockk<GitCommand>()
        clearMocks(contractGitCommand)
        clearMocks(fileOperations)
        every { contractGitCommand.show("HEAD", relativeContractPath) }.returns(oldContract)

        every { gitCommand.fileIsInGitDir(contractPath) }.returns(true)
        every { gitCommand.relativeGitPath(contractPath) }.returns(Pair(contractGitCommand, relativeContractPath))

        every { fileOperations.read(contractPath) }.returns(newContract)
    }

    @Test
    fun `compatibility check between file in working tree and HEAD should succeed if nothing is found in HEAD`() {
        val newContract = """
            Feature: Random number
              Scenario: Random number
                When GET /
                Then status 200
                And response-body (number)
        """.trimIndent()

        val contractGitCommand = mockk<GitCommand>()
        every { contractGitCommand.show("HEAD", relativeContractPath) }.answers { throw NonZeroExitError("ERROR", 1) }
        every { gitCommand.fileIsInGitDir(contractPath) }.returns(true)
        every { gitCommand.relativeGitPath(contractPath) }.returns(Pair(contractGitCommand, relativeContractPath))
        every { fileOperations.isFile(any()) }.returns(true)
        every { fileOperations.isDirectory(any()) }.returns(false)
        every { fileOperations.read(contractPath) }.returns(newContract)

        val exitCode = CommandLine(compatibleCommand, factory).execute("git", "file", contractPath)
        assertThat(exitCode).isZero()
    }

    @Test
    fun `compatibility check between file in working tree and HEAD should succeed if file path is not found`() {
        val newContract = """
            Feature: Random number
              Scenario: Random number
                When GET /
                Then status 200
                And response-body (number)
        """.trimIndent()

        val contractGitCommand = mockk<GitCommand>()
        every { contractGitCommand.show("HEAD", relativeContractPath) }.returns(newContract)
        every { gitCommand.fileIsInGitDir(contractPath) }.returns(true)
        every { gitCommand.relativeGitPath(contractPath) }.returns(Pair(contractGitCommand, relativeContractPath))
        every { fileOperations.isFile(any()) }.returns(true)
        every { fileOperations.isDirectory(any()) }.returns(false)
        every { fileOperations.read(contractPath) }.answers { throw FileNotFoundException() }

        val exitCode = CommandLine(compatibleCommand, factory).execute("git", "file", contractPath)
        assertThat(exitCode).isZero()
    }

    @Test
    fun `should check compatibility between two commits in a repo`() {
        val newCommitHash = "commit456"
        val oldCommitHash = "commit123"

        val contractPath = "/path/to/api.$CONTRACT_EXTENSION"
        val relativeContractPath = "api.$CONTRACT_EXTENSION"

        val contract = """
            Feature: Random number
              Scenario: Random number
                When GET /
                Then status 200
                And response-body (number)
        """.trimIndent()

        val gitRoot = mockk<GitCommand>()

        every { gitCommand.relativeGitPath(contractPath) }.returns(Pair(gitRoot, relativeContractPath))
        every { fileOperations.isFile(any()) }.returns(true)
        every { fileOperations.isDirectory(any()) }.returns(false)
        every { gitRoot.show(oldCommitHash, relativeContractPath) }.returns(contract)
        every { gitRoot.show(newCommitHash, relativeContractPath) }.returns(contract)

        val exitCode = CommandLine(compatibleCommand, factory).execute("git", "commits", contractPath, newCommitHash, oldCommitHash)
        assertThat(exitCode).isZero()
    }

    @Test
    fun `compatibility check between two commits in a repo should fail if old commit is missing`() {
        val newCommitHash = "commit456"
        val oldCommitHash = "commit123"

        val contractPath = "/path/to/api.$CONTRACT_EXTENSION"
        val relativeContractPath = "api.$CONTRACT_EXTENSION"

        val contract = """
            Feature: Random number
              Scenario: Random number
                When GET /
                Then status 200
                And response-body (number)
        """.trimIndent()

        val gitRoot = mockk<GitCommand>()

        every { gitCommand.relativeGitPath(contractPath) }.returns(Pair(gitRoot, relativeContractPath))
        every { gitRoot.show(oldCommitHash, relativeContractPath) }.answers { throw NonZeroExitError("Commit not found", 1) }
        every { gitRoot.show(newCommitHash, relativeContractPath) }.returns(contract)

        val exitCode = CommandLine(compatibleCommand, factory).execute("git", "commits", contractPath, newCommitHash, oldCommitHash)
        assertThat(exitCode).isOne()
    }

    @Test
    fun `compatibility check between two commits in a repo should fail if new commit is missing`() {
        val newCommitHash = "commit456"
        val oldCommitHash = "commit123"

        val contractPath = "/path/to/api.$CONTRACT_EXTENSION"
        val relativeContractPath = "api.$CONTRACT_EXTENSION"

        val contract = """
            Feature: Random number
              Scenario: Random number
                When GET /
                Then status 200
                And response-body (number)
        """.trimIndent()

        val gitRoot = mockk<GitCommand>()

        every { gitCommand.relativeGitPath(contractPath) }.returns(Pair(gitRoot, relativeContractPath))
        every { gitRoot.show(newCommitHash, relativeContractPath) }.answers { throw NonZeroExitError("Commit not found", 1) }
        every { gitRoot.show(oldCommitHash, relativeContractPath) }.returns(contract)

        val exitCode = CommandLine(compatibleCommand, factory).execute("git", "commits", contractPath, newCommitHash, oldCommitHash)
        assertThat(exitCode).isOne()
    }
}
