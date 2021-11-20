package `in`.specmatic.core.pattern

import `in`.specmatic.core.FailureReport
import `in`.specmatic.core.Result
import `in`.specmatic.core.Scenario
import `in`.specmatic.core.toReport

data class ContractException(val errorMessage: String = "", val breadCrumb: String = "", val exceptionCause: ContractException? = null, val scenario: Scenario? = null) : Exception(errorMessage) {
    constructor(failureReport: FailureReport): this(failureReport.toText())

    fun failure(): Result.Failure =
        Result.Failure(errorMessage, exceptionCause?.failure(), breadCrumb).also { result ->
            if(scenario != null) result.updateScenario(scenario)
        }

    fun report(): String = failure().toReport().toText()
}

fun <ReturnType> attempt(errorMessage: String = "", breadCrumb: String = "", f: ()->ReturnType): ReturnType {
    try {
        return f()
    }
    catch(contractException: ContractException) {
        throw ContractException(errorMessage, breadCrumb, contractException)
    }
    catch(throwable: Throwable) {
        throw ContractException("$errorMessage\nException thrown: $throwable", breadCrumb)
    }
}

fun <ReturnType> attempt(f: ()->ReturnType): ReturnType {
    try {
        return f()
    }
    catch(throwable: Throwable) {
        throw ContractException("Exception thrown: ${throwable.localizedMessage}")
    }
}

inline fun <ReturnType> scenarioBreadCrumb(scenario: Scenario, f: ()->ReturnType): ReturnType {
    try {
        return f()
    } catch(e: ContractException) {
        throw e.copy(scenario = scenario)
    }
}

fun resultOf(f: () -> Result): Result {
    return try {
        f()
    } catch(e: Throwable) { Result.Failure(e.localizedMessage) }
}
