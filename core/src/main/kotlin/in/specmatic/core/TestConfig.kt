package `in`.specmatic.core

import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.pattern.parsedJSON
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.Value
import java.io.File

private const val VARIABLES_KEY = "variables"
private const val BASE_URLS_KEY = "baseurls"

fun loadTestConfig(config: JSONObjectValue): TestConfig {
    val variables = readFromConfig(config, VARIABLES_KEY)
    val baseURLs = readFromConfig(config, BASE_URLS_KEY)

    return TestConfig(variables, baseURLs)
}

private fun readFromConfig(config: JSONObjectValue, key: String) =
    config.findFirstChildByName(key)?.let {
        if (it !is JSONObjectValue)
            throw ContractException("The \"$key\" key in the given config file must contain a JSON object.")

        it.jsonObject.mapValues { entry -> entry.value.toStringLiteral() }
    } ?: emptyMap()

data class TestConfig(val variables: Map<String, String>, val baseURLs: Map<String, String>) {
    fun withVariablesFromFilePath(variablesFileName: String?): TestConfig {
        if(variablesFileName == null)
            return this

        val variablesConfig = readVariablesFromFile(variablesFileName)
        val additionalVariables = parseVariables(variablesConfig)

        return this.copy(variables = this.variables.plus(additionalVariables))
    }

    private fun readVariablesFromFile(variablesFileName: String): Value {
        val variablesFile = File(variablesFileName).also {
            if (!it.exists())
                throw ContractException("Could not find a file named $variablesFileName")
        }

        return parsedJSON(variablesFile.readText())
    }

    private fun parseVariables(variablesConfig: Value): Map<String, String> {
        variablesConfig as JSONObjectValue

        return variablesConfig.jsonObject.mapValues {
            it.value.toStringLiteral()
        }
    }

}