package `in`.specmatic.core

import `in`.specmatic.core.pattern.ContractException

class ContractCache(private val executedContracts: MutableMap<String, Map<String, String>> = mutableMapOf()) {
    fun update(path: String, fn: () -> Map<String, String>): Map<String, String> {
        synchronized(this) {
            val data = fn()
            executedContracts[path] = data
            return data
        }
    }

    fun lookup(absolutePath: String): Map<String, String>? {
        return executedContracts[absolutePath]
    }
}

data class References(val valueName: String, val contractFile: ContractFileWithExports, val baseURLs: Map<String, String> = emptyMap(), private val variables: Map<String, String> = emptyMap(), private var valuesCache: Map<String, String>? = null, private val contractCache: ContractCache) {
    fun lookup(key: String): String {
        return fetchAndCache()[key] ?: throw ContractException("Key \"$key\" not found in value named $valueName")
    }

    private fun fetchAndCache(): Map<String, String> =
        contractCache.lookup(contractFile.absolutePath) ?: fetchAndUpdateContractCache()

    private fun fetchAndUpdateContractCache(): Map<String, String> = contractCache.update(contractFile.absolutePath) {
            contractFile.runContractAndExtractExports(valueName, baseURLs, variables)
        }
}
