package io.specmatic.stub

import io.specmatic.core.MismatchMessages
import io.specmatic.core.utilities.capitalizeFirstChar

object ContractExternalResponseMismatch: MismatchMessages {
    override fun mismatchMessage(expected: String, actual: String): String {
        return "Contract expected $expected but response from external command contained $actual"
    }

    override fun unexpectedKey(keyLabel: String, keyName: String): String {
        return "${keyLabel.lowercase().capitalizeFirstChar()} named $keyName in the response from the external command was not in the contract"
    }

    override fun expectedKeyWasMissing(keyLabel: String, keyName: String): String {
        return "${keyLabel.lowercase().capitalizeFirstChar()} named $keyName in the specification was not found in the response from the external command"
    }
}