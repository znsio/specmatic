package io.specmatic.test.report

import io.specmatic.core.TestResult
import io.specmatic.core.pattern.ContractException
import io.specmatic.test.report.interfaces.TestResultRecord

enum class Remarks(val value: String) {
    Covered("covered"),
    Missed("missing in spec"),
    NotImplemented("not implemented"),
    NotCovered("not covered"),
    Wip("WIP"),
    Invalid("invalid");

    override fun toString(): String {
        return value
    }

    companion object{
        fun resolve(testResultRecords: List<TestResultRecord>): Remarks {
            if(testResultRecords.any { it.isWip }) {
                return Wip
            }

            if(!testResultRecords.any { it.isValid }) {
                return when (testResultRecords.first().testResult) {
                    TestResult.MissingInSpec -> Missed
                    else -> Invalid
                }
            }

            if (testResultRecords.any { it.isExercised }) {
                return when (testResultRecords.first().testResult) {
                    TestResult.NotImplemented -> NotImplemented
                    else -> Covered
                }
            }

            return when (val result = testResultRecords.first().testResult) {
                TestResult.NotCovered -> NotCovered
                TestResult.MissingInSpec -> Missed
                else -> throw ContractException("Cannot determine remarks for unknown test result: $result")
            }
        }
    }
}