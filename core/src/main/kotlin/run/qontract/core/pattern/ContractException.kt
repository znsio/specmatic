package run.qontract.core.pattern

import run.qontract.core.Result
import run.qontract.core.Scenario
import run.qontract.core.resultReport

data class ContractException(val errorMessage: String = "", val breadCrumb: String = "", val exceptionCause: ContractException? = null, val scenario: Scenario? = null) : Exception(errorMessage) {
    fun result(): Result.Failure =
        Result.Failure(errorMessage, exceptionCause?.result(), breadCrumb).also { result ->
            if(scenario != null) result.updateScenario(scenario)
        }

    fun report(): String = resultReport(result())
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

fun <ReturnType> scenarioBreadCrumb(scenario: Scenario, f: ()->ReturnType): ReturnType {
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
