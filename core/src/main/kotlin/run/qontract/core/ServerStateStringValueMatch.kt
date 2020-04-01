package run.qontract.core

import run.qontract.core.pattern.convertStringToCorrectType
import run.qontract.core.value.Value

class ServerStateStringValueMatch(serverState: HashMap<String, Any> = HashMap()) : ServerStateMatch(serverState) {
    override fun match(sampleValue: Any, key: String): Result {
        val serverStateValue = convertStringToCorrectType(serverState[key])
        val typedSampleValue = when(sampleValue) {
            is Value -> sampleValue.value
            else -> convertStringToCorrectType(sampleValue)
        }

        return when(typedSampleValue == serverStateValue) {
            true -> Result.Success()
            false -> Result.Failure("ServerStateStringValue did not match. Expected: $serverStateValue Actual: $typedSampleValue")
        }
    }

    override fun copy() = ServerStateStringValueMatch(serverState)
}