package `in`.specmatic.core

import `in`.specmatic.core.Result.Failure
import `in`.specmatic.core.pattern.Pattern
import `in`.specmatic.core.value.Value

sealed class Result {
    var scenario: Scenario? = null
    var contractPath: String? = null

    fun reportString(): String {
        return toReport().toText()
    }

    fun updateScenario(scenario: Scenario): Result {
        this.scenario = scenario
        return this
    }

    abstract fun isTrue(): Boolean

    abstract fun ifSuccess(function: () -> Result): Result
    abstract fun withBindings(bindings: Map<String, String>, response: HttpResponse): Result

    fun updatePath(path: String): Result {
        this.contractPath = path
        return this
    }

    fun toReport(scenarioMessage: String? = null): Report {
        return when (this) {
            is Failure -> toFailureReport(scenarioMessage)
            else -> SuccessReport
        }
    }

    data class Failure(val message: String="", var cause: Failure? = null, val breadCrumb: String = "", val failureReason: FailureReason? = null) : Result() {
        override fun ifSuccess(function: () -> Result) = this
        override fun withBindings(bindings: Map<String, String>, response: HttpResponse): Result {
            return this
        }

        fun reason(errorMessage: String) = Failure(errorMessage, this)
        fun breadCrumb(breadCrumb: String) = Failure(cause = this, breadCrumb = breadCrumb)

        fun toFailureReport(scenarioMessage: String? = null): FailureReport {
            return FailureReport(contractPath, scenarioMessage, scenario, toMatchFailureDetails())
        }

        fun toMatchFailureDetails(): MatchFailureDetails =
            (cause?.toMatchFailureDetails() ?: MatchFailureDetails()).let { reason ->
                when {
                    message.isNotEmpty() -> reason.copy(errorMessages = listOf(message).plus(reason.errorMessages))
                    else -> reason
                }
            }.let { reason ->
                when {
                    breadCrumb.isNotEmpty() -> reason.copy(breadCrumbs = listOf(breadCrumb).plus(reason.breadCrumbs))
                    else -> reason
                }
            }

        override fun isTrue() = false
    }

    data class Success(val variables: Map<String, String> = emptyMap()) : Result() {
        override fun isTrue() = true
        override fun ifSuccess(function: () -> Result) = function()
        override fun withBindings(bindings: Map<String, String>, response: HttpResponse): Result {
            return this.copy(variables = response.export(bindings))
        }
    }
}

enum class FailureReason {
    PartNameMisMatch,
    URLPathMisMatch,
    SOAPActionMismatch
}

fun Result.breadCrumb(breadCrumb: String): Result =
    when(this) {
        is Failure -> this.breadCrumb(breadCrumb)
        else -> this
    }

data class MatchFailureDetails(val breadCrumbs: List<String> = emptyList(), val errorMessages: List<String> = emptyList(), val path: String? = null)

fun mismatchResult(expected: String, actual: String): Failure = Failure("Expected $expected, actual was $actual")
fun mismatchResult(expected: String, actual: Value?): Failure = mismatchResult(expected, valueError(actual) ?: "null")
fun mismatchResult(expected: Value, actual: Value?): Result = mismatchResult(valueError(expected) ?: "null", valueError(actual) ?: "nothing")
fun mismatchResult(expected: Pattern, actual: String): Failure = mismatchResult(expected.typeName, actual)
fun mismatchResult(pattern: Pattern, sampleData: Value?): Failure = mismatchResult(pattern, sampleData?.toStringLiteral() ?: "null")
fun mismatchResult(thisPattern: Pattern, otherPattern: Pattern): Failure {
    return mismatchResult(thisPattern.typeName, otherPattern.typeName)
}

fun valueError(value: Value?): String? {
    return value?.let { "${it.displayableType()}: ${it.displayableValue()}" }
}

interface Report {
    override fun toString(): String
    fun toText(): String
}

object SuccessReport: Report {
    override fun toString(): String = toText()

    override fun toText(): String {
        return ""
    }
}

class FailureReport(val contractPath: String?, val scenarioMessage: String?, val scenario: Scenario?, val matchFailureDetails: MatchFailureDetails): Report {
    override fun toText(): String {
        val contractLine = contractPathDetails()
        val scenarioDetails = scenarioDetails(scenario) ?: ""

        val matchFailureDetails = matchFailureDetails()

        val reportDetails = "$scenarioDetails${System.lineSeparator()}${System.lineSeparator()}${matchFailureDetails.prependIndent("  ")}"

        val report = contractLine?.let {
            val reportIndent = if(contractLine.isNotEmpty()) "  " else ""
            "$contractLine${reportDetails.prependIndent(reportIndent)}"
        } ?: reportDetails

        return report.trim()
    }

    override fun toString(): String = toText()

    private fun matchFailureDetails(): String {
        return matchFailureDetails.let { (breadCrumbs, errorMessages) ->
            val breadCrumbString =
                breadCrumbs
                    .filter { it.isNotBlank() }
                    .joinToString(".") { it.trim() }
                    .let {
                        when {
                            it.isNotBlank() -> ">> $it"
                            else -> ""
                        }
                    }

            val errorMessagesString = errorMessages.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")

            "$breadCrumbString${System.lineSeparator()}${System.lineSeparator()}$errorMessagesString".trim()
        }
    }

    private fun contractPathDetails(): String? {
        if(contractPath == null || contractPath.isBlank())
            return null

        return "Error from contract $contractPath\n\n"
    }

    private fun scenarioDetails(scenario: Scenario?): String? {
        return scenario?.let {
            val scenarioLine = """${scenarioMessage ?: "In scenario"} "${scenario.name}""""
            val urlLine =
                "API: ${scenario.httpRequestPattern.method} ${scenario.httpRequestPattern.urlMatcher?.path} -> ${scenario.httpResponsePattern.status}"

            "$scenarioLine${System.lineSeparator()}$urlLine"
        }
    }
}

fun toReport(result: Result, scenarioMessage: String? = null): String {
    return when (result) {
        is Failure -> {
            result.toFailureReport(scenarioMessage)
        }
        else -> SuccessReport
    }.toString()
}

fun shouldBeIgnored(result: Result): Boolean {
    return when(result) {
        is Result.Success -> false
        is Failure -> result.scenario?.ignoreFailure == true
    }
}