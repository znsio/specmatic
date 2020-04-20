package application

import run.qontract.core.utilities.valueMapToPlainJsonString
import run.qontract.core.value.StringValue
import run.qontract.core.value.Value

data class GitPointerInfo(val repoName: String, val contractPath: String) : PointerInfo {
    constructor(json: Map<String, Value>) : this(json.getValue("repoName").toStringValue(), json.getValue("contractPath").toStringValue())

    override fun toJSONString(): String {
        return valueMapToPlainJsonString(toMap())
    }

    private fun toMap() = mapOf("repoName" to StringValue(repoName), "contractPath" to StringValue(contractPath))
}
