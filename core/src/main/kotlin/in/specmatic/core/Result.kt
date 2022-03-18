package `in`.specmatic.core

import `in`.specmatic.core.Result.Failure
import `in`.specmatic.core.pattern.Pattern
import `in`.specmatic.core.utilities.capitalizeFirstChar
import `in`.specmatic.core.value.Value

sealed class Result {
    var scenario: Scenario? = null
    var contractPath: String? = null

    fun reportString(): String {
        return toReport().toText()
    }

    fun isFluffy(): Boolean {
        return isFluffy(0)
    }

    fun isFluffy(acceptableFluffLevel: Int): Boolean {
        return when(this) {
            is Failure ->
                failureReason?.let { it.fluffLevel > acceptableFluffLevel } == true || cause?.isFluffy() == true
            else -> false
        }
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

    data class FailureCause(val message: String="", var cause: Failure? = null, val breadCrumb: String = "")

    data class Failure(val causes: List<FailureCause> = emptyList(), val failureReason: FailureReason? = null) : Result() {
        constructor(message: String="", cause: Failure? = null, breadCrumb: String = "", failureReason: FailureReason? = null): this(listOf(FailureCause(message, cause, breadCrumb)), failureReason)

        companion object {
            fun fromFailures(failures: List<Failure>): Failure {
                val allCauses = failures.flatMap {
                    it.causes
                }

                return Failure(allCauses)
            }
        }

        fun copyWithDetails(breadCrumb: String, failureReason: FailureReason): Failure {
            val newCauses = causes.map {
                it.copy(breadCrumb = breadCrumb)
            }

            return this.copy(causes = newCauses, failureReason = failureReason)
        }

        val message = causes.firstOrNull()?.message ?: ""
        val cause = causes.firstOrNull()?.cause
        val breadCrumb: String = causes.firstOrNull()?.breadCrumb ?: ""

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

enum class FailureReason(val fluffLevel: Int) {
    PartNameMisMatch(0),
    URLPathMisMatch(2),
    SOAPActionMismatch(2),
    StatusMismatch(1),
    RequestMismatchButStatusAlsoWrong(1)
}

fun Result.breadCrumb(breadCrumb: String): Result =
    when(this) {
        is Failure -> this.breadCrumb(breadCrumb)
        else -> this
    }

data class MatchFailureDetails(val breadCrumbs: List<String> = emptyList(), val errorMessages: List<String> = emptyList(), val path: String? = null)

interface MismatchMessages {
    fun mismatchMessage(expected: String, actual: String): String
    fun unexpectedKey(keyLabel: String, keyName: String): String
    fun expectedKeyWasMissing(keyLabel: String, keyName: String): String
}

object DefaultMismatchMessages: MismatchMessages {
    override fun mismatchMessage(expected: String, actual: String): String {
        return "Expected $expected, actual was $actual"
    }

    override fun unexpectedKey(keyLabel: String, keyName: String): String {
        return "${keyLabel.lowercase().capitalizeFirstChar()} named \"$keyName\" was unexpected"
    }

    override fun expectedKeyWasMissing(keyLabel: String, keyName: String): String {
        return "Expected ${keyLabel.lowercase()} named \"$keyName\" was missing"
    }
}

fun mismatchResult(expected: String, actual: String, mismatchMessages: MismatchMessages = DefaultMismatchMessages): Failure = Failure(mismatchMessages.mismatchMessage(expected, actual))
fun mismatchResult(expected: String, actual: Value?, mismatchMessages: MismatchMessages = DefaultMismatchMessages): Failure = mismatchResult(expected, valueError(actual) ?: "null", mismatchMessages)
fun mismatchResult(expected: Value, actual: Value?, mismatchMessages: MismatchMessages = DefaultMismatchMessages): Result = mismatchResult(valueError(expected) ?: "null", valueError(actual) ?: "nothing", mismatchMessages)
fun mismatchResult(expected: Pattern, actual: String, mismatchMessages: MismatchMessages = DefaultMismatchMessages): Failure = mismatchResult(expected.typeName, actual, mismatchMessages)
fun mismatchResult(pattern: Pattern, sampleData: Value?, mismatchMessages: MismatchMessages = DefaultMismatchMessages): Failure = mismatchResult(pattern, sampleData?.toStringLiteral() ?: "null", mismatchMessages)
fun mismatchResult(thisPattern: Pattern, otherPattern: Pattern, mismatchMessages: MismatchMessages = DefaultMismatchMessages): Failure {
    return mismatchResult(thisPattern.typeName, otherPattern.typeName, mismatchMessages)
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