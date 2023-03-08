package `in`.specmatic.core.pattern

import `in`.specmatic.core.FailureReport
import `in`.specmatic.core.Result
import `in`.specmatic.core.Scenario
import `in`.specmatic.core.ScenarioDetailsForResult

<<<<<<< HEAD
fun isCycle(throwable: Throwable?): Boolean = when(throwable) {
    is ContractException -> throwable.isCycle
    else -> false
}

data class ContractException(
    val errorMessage: String = "",
    val breadCrumb: String = "",
    val exceptionCause: Throwable? = null,
    val scenario: ScenarioDetailsForResult? = null,
    val isCycle: Boolean = isCycle(exceptionCause)
) : Exception(errorMessage) {
    constructor(failureReport: FailureReport): this(failureReport.toText())

    fun failure(): Result.Failure =
        Result.Failure(errorMessage,
            if (exceptionCause is ContractException) exceptionCause.failure() else null,
            breadCrumb
        ).also { result ->
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
        throw ContractException("$errorMessage\nException thrown: $throwable", breadCrumb, throwable)
    }
}

fun <ReturnType> attempt(f: ()->ReturnType): ReturnType {
    try {
        return f()
    }
    catch(throwable: Throwable) {
        throw ContractException("Exception thrown: ${throwable.localizedMessage}", exceptionCause = throwable)
    }
}

inline fun <ReturnType> scenarioBreadCrumb(scenario: ScenarioDetailsForResult, f: ()->ReturnType): ReturnType {
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
