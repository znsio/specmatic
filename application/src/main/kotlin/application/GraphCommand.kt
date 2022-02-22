package application

import `in`.specmatic.core.SpecmaticConfigJson
import `in`.specmatic.core.git.getPersonalAccessToken
import `in`.specmatic.core.log.CompositePrinter
import `in`.specmatic.core.log.Verbose
import `in`.specmatic.core.log.logger
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.pattern.parsedJSON
import `in`.specmatic.core.utilities.ExternalCommand
import `in`.specmatic.core.value.JSONArrayValue
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.Value
import picocli.CommandLine
import picocli.CommandLine.Option
import java.util.*
import java.util.concurrent.Callable

interface AzureAuthToken {
    fun basic(): String
}

data class PersonalAccessToken(private val token: String): AzureAuthToken {
    override fun basic(): String {
        return String(Base64.getEncoder().encode("$token:".encodeToByteArray()))
    }
}

class AzureAPI(private val azureAuthToken: AzureAuthToken, private val azureBaseDirectory: String, private val collection: String) {
    data class ContractConsumerEntry(val collection: String, val project: String, val branch: String) {
        constructor(collection: String, jsonObject: JSONObjectValue): this(undefault(jsonObject, collection), jsonObject.jsonObject["project"]!!.toStringLiteral(), jsonObject.jsonObject["branch"]!!.toStringLiteral())
        constructor(collection: String, jsonObject: Value): this(collection, jsonObject as JSONObjectValue)

        val description: String
          get() {
              return "${collection}/${project} (${branch})"
          }
    }

    fun referencesToContract(searchString: String): List<ContractConsumerEntry> {
        val jsonResponse = codeAdvancedSearch(searchString)
        val references: JSONArrayValue = jsonResponse.findFirstChildByPath("results.values") as JSONArrayValue

        return references.list.map { ref ->
            ContractConsumerEntry(collection, ref)
        }
    }

    private fun codeAdvancedSearch(searchString: String): JSONObjectValue {
//        val client = HttpClient("https://devops.jio.com")
//        val request = HttpRequest("POST",
//            URL("https://devops.jio.com/JioMobilityAndEnterprise/_apis/search/codeAdvancedQueryResults?api-version=5.1-preview.1")
//        ).copy(
//            headers = mapOf(
//                "Authorization" to "Basic M25ybTdreDdjZ2w1bGNhaWp4cXcyeHozMnQ0a3lyNHVxY21sZ2VjNDdxYnNhcnJzdGFlcTo="
//            ),
//            body = parsedJSON("""
//                {"searchText":"specmatic.json","skipResults":0,"takeResults":50,"sortOptions":[],"summarizedHitCountsNeeded":true,"searchFilters":{}}
//            """.trimIndent())
//        )
//        client.execute(request)

        val commandFragments = listOf(
            "curl",
            "-k",
            "-X",
            "POST",
            "-H",
            "Authorization: Basic ${azureAuthToken.basic()}",
            "-H",
            "Content-Type: application/json",
            "-d",
            """{"searchText":"$searchString","skipResults":0,"takeResults":50,"sortOptions":[],"summarizedHitCountsNeeded":true,"searchFilters":{}}""",
            "$azureBaseDirectory/$collection/_apis/search/codeAdvancedQueryResults?api-version=5.1-preview.1"
        )

        val cmd = ExternalCommand(
            commandFragments.toTypedArray(), ".", emptyList<String>().toTypedArray()
        )

        logger.debug(commandFragments.toString())

        val response = cmd.executeAsSeparateProcess()

        logger.debug(response)

        return parsedJSON(response) as JSONObjectValue
    }

}

private fun undefault(jsonObject: JSONObjectValue, defaultCollectionName: String) =
    when (val collection = jsonObject.jsonObject["collection"]!!.toStringLiteral()) {
        "DefaultCollection" -> defaultCollectionName
        else -> collection
    }


@CommandLine.Command(name = "graph",
    mixinStandardHelpOptions = true,
    description = ["Dependency graph"])
class GraphCommand: Callable<Unit> {
    @CommandLine.Command(name = "consumers", description = ["Display a list of services depending on contracts in this repo"])
    fun consumers(@Option(names = ["--verbose"], description = ["Print verbose logs"]) verbose: Boolean = false) {
        if(verbose)
            logger = Verbose(CompositePrinter())

        val config = SpecmaticConfig()

        val configJson = SpecmaticConfigJson.load()

        val azureAuthToken = PersonalAccessToken(getPersonalAccessToken() ?: throw ContractException("Access token not found, put it in ${System.getProperty("user.home")}/specmatic.json"))

        val collection = configJson.azure?.collectionName!!
        val azure = AzureAPI(azureAuthToken, "https://devops.jio.com", collection)

        logger.log("Dependency projects")
        logger.log("-------------------")

        configJson.sources.forEach { source ->
            logger.log("In central repo ${source.repository}")

            source.test?.forEach { relativeContractPath ->
                logger.log("  Consumers of $relativeContractPath")
                val consumers = azure.referencesToContract(relativeContractPath)

                if(consumers.isEmpty()) {
                    logger.log("    ** no consumers found **")
                } else {
                    consumers.forEach {
                        logger.log("  - ${it.description}")
                    }
                }

                logger.newLine()
            }
        }
    }

    override fun call() {
    }

}