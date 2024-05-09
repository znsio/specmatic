package application

import `in`.specmatic.core.Feature
import `in`.specmatic.core.Result
import `in`.specmatic.core.Results
import `in`.specmatic.core.git.GitCommand
import `in`.specmatic.core.testBackwardCompatibility
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream

class BackwardCompatibilityCheckCommandTest {
    private val standardOut = System.out
    private val outputStreamCaptor = ByteArrayOutputStream()

    private val gitCommand = mockk<GitCommand> {
        every {
            getFileInTheDefaultBranch(any(), any())
        } returns null
        every { currentBranch() } returns "PR_BRANCH"
        every { checkout(any()) } returns mockk()
    }
    private val command = BackwardCompatibilityCheckCommand(gitCommand)

    private val productAPISpec = this::class.java.classLoader.getResource("BCCheckCommandTest/product-api.yaml")?.path!!
    private val orderAPISpec = this::class.java.classLoader.getResource("BCCheckCommandTest/order-api.yml")?.path!!
    private val commonAPISpec = this::class.java.classLoader.getResource("BCCheckCommandTest/common.yaml")?.path!!
    private val commonAPISpecOnDefaultBranch = this::class.java.classLoader.getResource("BCCheckCommandTest/common-on-default-branch.yaml")?.path!!

    @BeforeEach
    fun setup() {
        // NOTE - remove this line if you want to see actual test logs in stdout
        System.setOut(PrintStream(outputStreamCaptor))
        mockkStatic("in.specmatic.core.TestBackwardCompatibilityKt")
    }

    @AfterEach
    fun tearDown() {
        System.setOut(standardOut)
        unmockkAll()
    }

    @Test
    fun `should pass when all the changed OpenAPI specs are backward compatible or new`() {
        every {
            gitCommand.getFilesChangeInCurrentBranch()
        } returns listOf(commonAPISpec)

        every {
            gitCommand.getFileInTheDefaultBranch(commonAPISpec, any())
        } returns File(commonAPISpecOnDefaultBranch)

        every {
            testBackwardCompatibility(any<Feature>(), any<Feature>())
        } returns Results(listOf(Result.Success()))

        command.call()

        val printedOutput = outputStreamCaptor.toString().trim()
        printedOutput shouldContain "Verdict: PASS, all changes were backward compatible"
    }

    @Test
    fun `should fail when one of the changed OpenAPI specs is backward incompatible`() {
    }

    @Test
    fun `should not run backward compatibility check if no OpenAPI specs were changed`() {
    }


}