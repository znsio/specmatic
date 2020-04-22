package run.qontract.core.versioning

import run.qontract.core.utilities.valueMapToPlainJsonString
import run.qontract.core.value.StringValue
import run.qontract.core.value.Value

const val POINTER_EXTENSION = "pointer"

data class PointerInfo(val repoName: String, val contractPath: String) {
    constructor(json: Map<String, Value>) : this(json.getValue("repoName").toStringValue(), json.getValue("contractPath").toStringValue())

    fun toJSONString(): String {
        return valueMapToPlainJsonString(toMap())
    }

    private fun toMap() = mapOf("repoName" to StringValue(repoName), "contractPath" to StringValue(contractPath))
}
