package io.specmatic.core

import io.specmatic.core.Result.Failure
import io.specmatic.core.pattern.ContractException

sealed class KeyError {
    abstract val name: String

    abstract fun missingKeyToResult(keyLabel: String, mismatchMessages: MismatchMessages = DefaultMismatchMessages): Failure

    abstract fun missingOptionalKeyToResult(keyLabel: String, mismatchMessages: MismatchMessages = DefaultMismatchMessages): Failure
}

data class MissingKeyError(override val name: String) : KeyError() {
    override fun missingKeyToResult(keyLabel: String, mismatchMessages: MismatchMessages): Failure =
        Failure(mismatchMessages.expectedKeyWasMissing(keyLabel, name))

    override fun missingOptionalKeyToResult(keyLabel: String, mismatchMessages: MismatchMessages): Failure {
        return Failure(mismatchMessages.optionalKeyMissing(keyLabel, name), isPartial = true)
    }
}

data class UnexpectedKeyError(override val name: String) : KeyError() {
    override fun missingKeyToResult(keyLabel: String, mismatchMessages: MismatchMessages): Failure =
        Failure(mismatchMessages.unexpectedKey(keyLabel, name))

    override fun missingOptionalKeyToResult(keyLabel: String, mismatchMessages: MismatchMessages): Failure {
        throw ContractException("This should never happen")
    }
}
