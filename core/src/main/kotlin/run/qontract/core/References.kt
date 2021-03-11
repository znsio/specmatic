package run.qontract.core

import run.qontract.core.pattern.ContractException
import run.qontract.test.HttpClient
import java.io.File

data class References(val valueName: String, val qontractFileName: String, val baseURL: String? = null, private var valuesCache: Map<String, String>? = null) {
    fun lookup(key: String): String {
        return refresh()[key] ?: throw ContractException("Key not found in value named $valueName")
    }

    private fun refresh(): Map<String, String> {
        val localCopy = valuesCache

        if(localCopy != null)
            return localCopy

        val qontractFile = File(qontractFileName)
        if(!qontractFile.exists())
            throw ContractException("Qontract file $qontractFileName does not exist, but is used as the source of variables in value $valueName")

        val feature = Feature(qontractFile.readText())
        val results = feature.executeTests(HttpClient(baseURL ?: throw ContractException("Base URL for qontract file $qontractFileName was not supplied.")))

        val updatedValues = results.results.fold(mapOf<String, String>()) { acc, result ->
            if(result is Result.Success)
                acc.plus(result.variables)
            else
                acc
        }

        valuesCache = updatedValues

        return updatedValues
    }
}
