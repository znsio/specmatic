package `in`.specmatic.core

import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.test.HttpClient

data class References(val valueName: String, val qontractFilePath: QontractFilePath, val baseURLs: Map<String, String> = emptyMap(), private val variables: Map<String, String> = emptyMap(), private var valuesCache: Map<String, String>? = null) {
    fun lookup(key: String): String {
        return fetchAndCache()[key] ?: throw ContractException("Key \"$key\" not found in value named $valueName")
    }

    private fun fetchAndCache(): Map<String, String> {
        val localCopy = valuesCache

        if(localCopy != null)
            return localCopy

        val feature = qontractFilePath.readFeatureForValue(valueName).copy(testVariables = variables, testBaseURLs = baseURLs)
        val baseURL = baseURLs[qontractFilePath.path]
        val results = feature.executeTests(HttpClient(baseURL ?: throw ContractException("Base URL for spec file ${qontractFilePath.path} was not supplied.")))

        if(results.hasFailures()) {
            throw ContractException("There were failures when running ${qontractFilePath.path} as a test against URL $baseURL:\n" + results.report().prependIndent("  "))
        }

        val updatedValues = results.results.filterIsInstance<Result.Success>().fold(mapOf<String, String>()) { acc, result ->
            acc.plus(result.variables)
        }

        valuesCache = updatedValues

        return updatedValues
    }
}
