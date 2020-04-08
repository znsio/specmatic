package run.qontract.core.pattern

import run.qontract.core.Result
import run.qontract.core.Scenario

data class ContractException(val errorMessage: String = "", val breadCrumb: String = "", val exceptionCause: ContractException? = null, val scenario: Scenario? = null) : Exception(errorMessage) {
    fun result(): Result.Failure {
        return Result.Failure(errorMessage, exceptionCause?.result(), breadCrumb).also { result ->
            when(scenario) {
                null -> result
                else -> result.updateScenario(scenario)
            }
        }
    }
}

fun <ReturnType> attempt(errorMessage: String = "", breadCrumb: String = "", f: ()->ReturnType): ReturnType {
    try {
        return f()
    }
    catch(contractException: ContractException) {
        throw ContractException(errorMessage, breadCrumb, contractException)
    }
    catch(throwable: Throwable) {
        throw ContractException("$errorMessage\nException thrown: $throwable", breadCrumb, null)
    }
}

fun <ReturnType> scenarioBreadCrumb(scenario: Scenario, f: ()->ReturnType): ReturnType {
    try {
        return f()
    } catch(e: ContractException) {
        throw e.copy(scenario = scenario)
    }
}