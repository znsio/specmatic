package `in`.specmatic.stub

import `in`.specmatic.core.MismatchMessages

class ExamplesAsExpectationsMismatch(val exampleName: String) : MismatchMessages {
    override fun mismatchMessage(expected: String, actual: String): String {
        return "$actual in the example \"$exampleName\" does not match $expected in the spec"
    }

    override fun unexpectedKey(keyLabel: String, keyName: String): String {
        return "$keyLabel named $keyName in the example \"$exampleName\" was not found in the spec"
    }

    override fun expectedKeyWasMissing(keyLabel: String, keyName: String): String {
        return "$keyLabel named $keyName in the spec was not found in the \"$exampleName\" example"
    }

}