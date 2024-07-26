package io.specmatic.test

import io.specmatic.core.TestResult
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TestResultRecordTest {

    @Test
    fun `should not be exercised for results Skipped and DidNotRun`() {
        listOf(TestResult.Skipped, TestResult.DidNotRun).forEach {
            val record = TestResultRecord(
                path = "/example/path",
                method = "GET",
                responseStatus = 200,
                result = it
            )
            assertFalse(record.isExercised, "Record should not be exercised for Result: $it")
        }
    }

    @Test
    fun `should be exercised for other results`() {
        TestResult.entries.filterNot { it in listOf(TestResult.Skipped, TestResult.DidNotRun) }.forEach {
            val record = TestResultRecord(
                path = "/example/path",
                method = "GET",
                responseStatus = 200,
                result = it
            )
            assertTrue(record.isExercised, "Record should be exercised for Result: $it")
        }
    }

    @Test
    fun `should be covered for results Success, Error, Failed and Covered`() {
        listOf(TestResult.Success, TestResult.Error, TestResult.Failed, TestResult.Covered).forEach {
            val record = TestResultRecord(
                path = "/example/path",
                method = "GET",
                responseStatus = 200,
                result = it
            )
            assertTrue(record.isCovered, "Record should be covered for result $it")
        }
    }

    @Test
    fun `should not be covered for other results`() {
        TestResult.entries.filterNot { it in listOf(TestResult.Success, TestResult.Error, TestResult.Failed, TestResult.Covered) }.forEach {
            val record = TestResultRecord(
                path = "/example/path",
                method = "GET",
                responseStatus = 200,
                result = it
            )
            assertFalse(record.isCovered, "Record should not be covered for result $it")
        }
    }
}
