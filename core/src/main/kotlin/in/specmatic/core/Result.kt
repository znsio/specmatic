package `in`.specmatic.core

import `in`.specmatic.core.Result.Failure
import `in`.specmatic.core.pattern.Pattern
import `in`.specmatic.core.utilities.capitalizeFirstChar
import `in`.specmatic.core.value.Value

sealed class Result {
    var scenario: ScenarioDetailsForResult? = null
    var contractPath: String? = null

    companion object {
        fun fromFailures(failures: List<Failure>): Result {
            return if(failures.isNotEmpty())
                Failure.fromFailures(failures)
            else
                Success()
        }

        fun fromResults(results: List<Result>): Result {
            val failures = results.filterIsInstance<Failure>()
            return fromFailures(failures)
        }
    }

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

    fun updateScenario(scenario: ScenarioDetailsForResult): Result {
        this.scenario = scenario
        return this
    }

    abstract fun isSuccess(): Boolean

    abstract fun ifSuccess(function: () -> Result): Result
    abstract fun withBindings(bindings: Map<String, String>, response: HttpResponse): Result
    abstract fun breadCrumb(breadCrumb: String): Result
    abstract fun failureReason(failureReason: FailureReason?): Result

    abstract fun shouldBeIgnored(): Boolean

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

    abstract fun partialSuccess(message: String): Result
    abstract fun isPartialSuccess(): Boolean

    abstract fun testResult(): TestResult

    data class FailureCause(val message: String="", var cause: Failure? = null)

    data class Failure(val causes: List<FailureCause> = emptyList(), val breadCrumb: String = "", val failureReason: FailureReason? = null) : Result() {
        constructor(message: String="", cause: Failure? = null, breadCrumb: String = "", failureReason: FailureReason? = null): this(listOf(FailureCause(message, cause)), breadCrumb, failureReason)

        companion object {
            fun fromFailures(failures: List<Failure>): Failure {
                return Failure(failures.map {
                    it.toFailureCause()
                })
            }
        }

        val message = causes.firstOrNull()?.message ?: ""
        val cause = causes.firstOrNull()?.cause

        fun toFailureCause(): FailureCause {
            return FailureCause(cause = this)
        }

        override fun ifSuccess(function: () -> Result) = this
        override fun withBindings(bindings: Map<String, String>, response: HttpResponse): Result {
            return this
        }

        override fun shouldBeIgnored(): Boolean {
            return this.scenario?.ignoreFailure == true
        }

        override fun partialSuccess(message: String): Result {
            return this
        }

        override fun isPartialSuccess(): Boolean = false
        override fun testResult(): TestResult {
            if(shouldBeIgnored())
                return TestResult.Error

            return TestResult.Failed
        }

        fun reason(errorMessage: String) = Failure(errorMessage, this)
        override fun breadCrumb(breadCrumb: String) = Failure(cause = this, breadCrumb = breadCrumb)
        override fun failureReason(failureReason: FailureReason?): Result {
            return this.copy(failureReason = failureReason)
        }

        fun toFailureReport(scenarioMessage: String? = null): FailureReport {
            return FailureReport(contractPath, scenarioMessage, scenario, toMatchFailureDetailList())
        }

        fun toMatchFailureDetails(): MatchFailureDetails {
            return (cause?.toMatchFailureDetails() ?: MatchFailureDetails()).let { reason ->
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
        }

        fun toMatchFailureDetailList(): List<MatchFailureDetails> {
            return causes.flatMap {
                (it.cause?.toMatchFailureDetailList() ?: listOf(MatchFailureDetails())).map { matchFailureDetails ->
                    val withReason = when {
                        message.isNotEmpty() -> matchFailureDetails.copy(errorMessages = listOf(message).plus(matchFailureDetails.errorMessages))
                        else -> matchFailureDetails
                    }

                    when {
                        breadCrumb.isNotEmpty() -> withReason.copy(breadCrumbs = listOf(breadCrumb).plus(withReason.breadCrumbs))
                        else -> withReason
                    }
                }
            }
        }

        override fun isSuccess() = false
    }

    data class Success(val variables: Map<String, String> = emptyMap(), val partialSuccessMessage: String? = null) : Result() {
        override fun isSuccess() = true
        override fun ifSuccess(function: () -> Result) = function()
        override fun withBindings(bindings: Map<String, String>, response: HttpResponse): Result {
            return this.copy(variables = response.export(bindings))
        }

        override fun breadCrumb(breadCrumb: String): Result {
            return this
        }

        override fun failureReason(failureReason: FailureReason?): Result {
            return this
        }

        override fun shouldBeIgnored(): Boolean = false
        override fun partialSuccess(message: String): Result {
            return this.copy(partialSuccessMessage = message)
        }

        override fun isPartialSuccess(): Boolean = partialSuccessMessage != null
        override fun testResult(): TestResult {
            return TestResult.Success
        }
    }
}

enum class TestResult {
    Success,
    Error,
    Failed
}

enum class FailureReason(val fluffLevel: Int) {
    PartNameMisMatch(0),
    StatusMismatch(1),
    MethodMismatch(1),
    RequestMismatchButStatusAlsoWrong(1),
    URLPathMisMatch(2),
    SOAPActionMismatch(2)
}

fun Result.breadCrumb(breadCrumb: String): Result =
    when(this) {
        is Failure -> this.breadCrumb(breadCrumb)
        else -> this
    }

data class MatchFailureDetails(val breadCrumbs: List<String> = emptyList(), val errorMessages: List<String> = emptyList(), val path: String? = null) {
    private fun breadCrumbString(breadCrumbs: List<String>) {
        breadCrumbs
            .filter { it.isNotBlank() }
            .joinToString(".") { it.trim() }
            .let {
                when {
                    it.isNotBlank() -> ">> $it"
                    else -> ""
                }
            }
    }
}

interface MismatchMessages {
    fun mismatchMessage(expected: String, actual: String): String
    fun unexpectedKey(keyLabel: String, keyName: String): String
    fun expectedKeyWasMissing(keyLabel: String, keyName: String): String
    fun valueMismatchFailure(expected: String, actual: Value?, mismatchMessages: MismatchMessages = DefaultMismatchMessages): Failure {
        return mismatchResult(expected, valueError(actual) ?: "null", mismatchMessages)
    }
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
fun mismatchResult(expected: String, actual: Value?, mismatchMessages: MismatchMessages = DefaultMismatchMessages): Failure = mismatchMessages.valueMismatchFailure(expected, actual, mismatchMessages)
fun mismatchResult(expected: Value, actual: Value?, mismatchMessages: MismatchMessages = DefaultMismatchMessages): Result = mismatchResult(valueError(expected) ?: "null", valueError(actual) ?: "nothing", mismatchMessages)
fun mismatchResult(expected: Pattern, actual: String, mismatchMessages: MismatchMessages = DefaultMismatchMessages): Failure = mismatchResult(expected.typeName, actual, mismatchMessages)
fun mismatchResult(pattern: Pattern, sampleData: Value?, mismatchMessages: MismatchMessages = DefaultMismatchMessages): Failure = mismatchResult(pattern, sampleData?.toStringLiteral() ?: "null", mismatchMessages)
fun mismatchResult(thisPattern: Pattern, otherPattern: Pattern, mismatchMessages: MismatchMessages = DefaultMismatchMessages): Failure {
    return mismatchResult(thisPattern.typeName, otherPattern.typeName, mismatchMessages)
}

fun valueError(value: Value?): String? {
    return value?.valueErrorSnippet()
}
