package io.specmatic.test.reports.coverage.console

import io.specmatic.core.TestResult
import io.specmatic.core.pattern.ContractException
import io.specmatic.test.TestResultRecord

enum class Remarks(val value: String) {
    Covered("covered"),
    Missed("missing in spec"),
    NotImplemented("not implemented"),
    DidNotRun("did not run"),
    NotCovered("not covered"),
    Wip("WIP"),
    Invalid("invalid");

    override fun toString(): String {
        return value
    }

    companion object{
        fun resolve(testResultRecords: List<TestResultRecord>): Remarks {
            if(!testResultRecords.any { it.isValid }) {
                return when (testResultRecords.first().result) {
                    TestResult.MissingInSpec -> Missed
                    else -> Invalid
                }
            }

            if (testResultRecords.any { it.isExercised }) {
                return when (testResultRecords.first().result) {
                    TestResult.Wip -> Wip
                    TestResult.NotImplemented -> NotImplemented
                    TestResult.MissingInSpec -> Missed
                    TestResult.NotCovered -> NotCovered
                    else -> Covered
                }
            }

            return when (val result = testResultRecords.first().result) {
                TestResult.Skipped -> Missed
                TestResult.DidNotRun -> DidNotRun
                else -> throw ContractException("Cannot determine remarks for unknown test result: $result")
            }
        }
    }
}