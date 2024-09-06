package io.specmatic.test.report.interfaces

import io.specmatic.core.TestResult

interface TestResultRecord {
    val testResult: TestResult
    val isExercised: Boolean
    val isCovered: Boolean
    val isWip: Boolean
    val isValid: Boolean
}