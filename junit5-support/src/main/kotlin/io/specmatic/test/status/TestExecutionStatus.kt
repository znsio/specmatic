package io.specmatic.test.status

/**
 * Singleton class to track and manage test execution status,
 * including conditions that should result in non-zero exit codes.
 */
object TestExecutionStatus {
    private var noTestsRun: Boolean = false
    private var testFailure: Boolean = false
    private var exitWithErrorOnNoTests: Boolean = true
    
    /**
     * Sets whether the exit code should be non-zero when no tests run
     */
    fun setExitWithErrorOnNoTests(value: Boolean) {
        exitWithErrorOnNoTests = value
    }
    
    /**
     * Marks that no tests were run during execution
     */
    fun markNoTestsRun() {
        noTestsRun = true
    }
    
    /**
     * Marks that a test failure occurred
     */
    fun markTestFailure() {
        testFailure = true
    }
    
    /**
     * Determines the exit code based on the current status
     * @return 0 for success, non-zero for failure
     */
    fun getExitCode(): Int {
        return when {
            testFailure -> 1
            noTestsRun && exitWithErrorOnNoTests -> 1
            else -> 0
        }
    }
    
    /**
     * Resets all status flags (primarily for testing)
     */
    fun reset() {
        noTestsRun = false
        testFailure = false
        exitWithErrorOnNoTests = true
    }
    
    /**
     * Check if no tests were run
     */
    fun hasNoTestsRun(): Boolean = noTestsRun
}