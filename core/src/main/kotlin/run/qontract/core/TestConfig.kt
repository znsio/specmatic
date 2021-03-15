package run.qontract.core

import run.qontract.core.pattern.ContractException
import run.qontract.core.pattern.parsedJSON
import run.qontract.core.value.JSONObjectValue
import run.qontract.core.value.Value
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

        it.jsonObject.mapValues { entry -> entry.value.toStringValue() }
    } ?: emptyMap()

data class TestConfig(val variables: Map<String, String>, val baseURLs: Map<String, String>)