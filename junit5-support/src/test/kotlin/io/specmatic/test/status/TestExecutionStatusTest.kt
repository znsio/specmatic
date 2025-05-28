package io.specmatic.test.status

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TestExecutionStatusTest {

    @BeforeEach
    fun setup() {
        // Reset the status before each test
        TestExecutionStatus.reset()
    }

    @Test
    fun `should return exit code 0 when no conditions are set`() {
        assertEquals(0, TestExecutionStatus.getExitCode())
    }

    @Test
    fun `should return exit code 1 when no tests run and exitWithErrorOnNoTests is true`() {
        TestExecutionStatus.markNoTestsRun()
        assertEquals(1, TestExecutionStatus.getExitCode())
    }

    @Test
    fun `should return exit code 0 when no tests run and exitWithErrorOnNoTests is false`() {
        TestExecutionStatus.setExitWithErrorOnNoTests(false)
        TestExecutionStatus.markNoTestsRun()
        assertEquals(0, TestExecutionStatus.getExitCode())
    }

    @Test
    fun `should return exit code 1 when test failure occurs`() {
        TestExecutionStatus.markTestFailure()
        assertEquals(1, TestExecutionStatus.getExitCode())
    }

    @Test
    fun `should return exit code 1 when both no tests run and test failure occur`() {
        TestExecutionStatus.markNoTestsRun()
        TestExecutionStatus.markTestFailure()
        assertEquals(1, TestExecutionStatus.getExitCode())
    }

    @Test
    fun `should return exit code 1 when test failure occurs even if exitWithErrorOnNoTests is false`() {
        TestExecutionStatus.setExitWithErrorOnNoTests(false)
        TestExecutionStatus.markTestFailure()
        assertEquals(1, TestExecutionStatus.getExitCode())
    }
}