package `in`.specmatic.core

import `in`.specmatic.core.Result.Failure
import `in`.specmatic.core.utilities.capitalizeFirstChar

sealed class KeyError {
    abstract val name: String

    abstract fun missingKeyToResult(keyLabel: String): Failure
}

data class MissingKeyError(override val name: String) : KeyError() {
    override fun missingKeyToResult(keyLabel: String): Failure =
        Failure("Expected ${keyLabel.lowercase()} named \"$name\" was missing")
}

data class UnexpectedKeyError(override val name: String) : KeyError() {
    override fun missingKeyToResult(keyLabel: String): Failure {
        return Failure("${keyLabel.lowercase().capitalizeFirstChar()} named \"$name\" was unexpected")
    }
}

internal fun String.toMissingKeyError(): MissingKeyError {
    return MissingKeyError(this)
}
