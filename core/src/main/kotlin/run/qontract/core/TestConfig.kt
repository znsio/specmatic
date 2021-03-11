package run.qontract.core

import run.qontract.core.pattern.ContractException
import run.qontract.core.pattern.parsedJSON
import run.qontract.core.value.JSONObjectValue
import java.io.File

fun loadTestConfig(configFile: String?): TestConfig {
    val config = configFile?.let {
        if(!File(configFile).exists())
            throw ContractException("Context file $configFile does not exist.")

        val context = File(configFile).readText()
        val contextJSON = parsedJSON(context)
        if(contextJSON !is JSONObjectValue)
            throw ContractException("The context file $configFile must contain a sinlge json object.")

        contextJSON
    } ?: JSONObjectValue()

    val variables = readFromConfig(config, "variables")
    val baseURLs = readFromConfig(config, "baseURLs")

    return TestConfig(variables, baseURLs)
}

private fun readFromConfig(config: JSONObjectValue, key: String) =
    config.findFirstChildByName(key)?.let {
        if (it !is JSONObjectValue)
            throw ContractException("The \"$key\" key in the given config file must contain a JSON object.")

        it.jsonObject.mapValues { it.value.toStringValue() }
    } ?: emptyMap()

data class TestConfig(val variables: Map<String, String>, val baseURLs: Map<String, String>) {

}