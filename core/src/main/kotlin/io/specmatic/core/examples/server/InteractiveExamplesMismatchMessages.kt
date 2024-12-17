package io.specmatic.core.examples.server

import io.specmatic.core.MismatchMessages
import io.specmatic.core.utilities.capitalizeFirstChar

object InteractiveExamplesMismatchMessages : MismatchMessages {
    override fun mismatchMessage(expected: String, actual: String): String {
        return "Specification expected $expected but example contained $actual"
    }

    override fun unexpectedKey(keyLabel: String, keyName: String): String {
        return "${keyLabel.capitalizeFirstChar()} $keyName in the example is not in the specification"
    }

    override fun optionalKeyMissing(keyLabel: String, keyName: String): String {
        return "Warning: Optional ${keyLabel.capitalizeFirstChar()} $keyName in the specification is missing from the example"
    }

    override fun expectedKeyWasMissing(keyLabel: String, keyName: String): String {
        return "${keyLabel.capitalizeFirstChar()} $keyName in the specification is missing from the example"
    }
}
