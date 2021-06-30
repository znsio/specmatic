package `in`.specmatic.core

import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.test.HttpClient
import java.io.File

data class ContractFileWithExports(val path: String, val relativeTo: RelativeTo = NoAnchor) {
    fun readFeatureForValue(valueName: String): Feature {
        return file.let {
            if(!it.exists())
                throw ContractException("$APPLICATION_NAME file $path does not exist, but is used as the source of variables in value $valueName")

            parseGherkinStringToFeature(it.readText(), it.canonicalPath)
        }
    }

    val file: File = relativeTo.resolve(path)

    val absolutePath: String = file.canonicalPath

    fun runContractAndExtractExports(valueName: String, baseURLs: Map<String, String>, variables: Map<String, String>): Map<String, String> {
        val feature = readFeatureForValue(valueName)
            .copy(testVariables = variables, testBaseURLs = baseURLs)

        val client = HttpClient(
            baseURLs[path] ?: throw ContractException("Base URL for spec file $path was not supplied.")
        )

        val results = feature.executeTests(client)

        if (results.hasFailures()) {
            throw ContractException(
                "There were failures when running $path as a test against URL ${baseURLs[path]}:\n" + results.report(
                    PATH_NOT_RECOGNIZED_ERROR
                ).prependIndent("  ")
            )
        }

        return results.results.filterIsInstance<Result.Success>().fold(mapOf()) { acc, result ->
            acc.plus(result.variables)
        }
    }
}