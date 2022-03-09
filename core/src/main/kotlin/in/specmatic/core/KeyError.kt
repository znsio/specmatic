package `in`.specmatic.core

import `in`.specmatic.core.Result.Failure
import `in`.specmatic.core.utilities.capitalizeFirstChar

sealed class KeyError {
    abstract val name: String

    abstract fun missingKeyToResult(keyLabel: String, mismatchMessages: MismatchMessages = DefaultMismatchMessages): Failure
}

data class MissingKeyError(override val name: String) : KeyError() {
    override fun missingKeyToResult(keyLabel: String, mismatchMessages: MismatchMessages): Failure =
        Failure(mismatchMessages.expectedKeyWasMissing(keyLabel, name))
}

data class UnexpectedKeyError(override val name: String) : KeyError() {
    override fun missingKeyToResult(keyLabel: String, mismatchMessages: MismatchMessages): Failure =
        Failure(mismatchMessages.unexpectedKey(keyLabel, name))
}

internal fun String.toMissingKeyError(): MissingKeyError {
    return MissingKeyError(this)
}
