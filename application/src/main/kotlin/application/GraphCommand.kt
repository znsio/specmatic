package application

import `in`.specmatic.core.HttpRequest
import `in`.specmatic.core.SpecmaticConfigJson
import `in`.specmatic.core.git.getPersonalAccessToken
import `in`.specmatic.core.log.CompositePrinter
import `in`.specmatic.core.log.Verbose
import `in`.specmatic.core.log.logger
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.pattern.parsedJSON
import `in`.specmatic.core.utilities.exitWithMessage
import `in`.specmatic.core.value.JSONArrayValue
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.Value
import `in`.specmatic.test.HttpClient
import picocli.CommandLine
import picocli.CommandLine.Option
import java.net.URI
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

class AzureAPI(private val azureAuthToken: AzureAuthToken, private val azureBaseURL: String, private val collection: String) {
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
        val client = HttpClient(azureBaseURL, log = { })
        val request = HttpRequest("POST",
            URI("/$collection/_apis/search/codeAdvancedQueryResults?api-version=5.1-preview.1")
        ).copy(
            headers = mapOf(
                "Authorization" to "Basic ${azureAuthToken.basic()}"
            ),
            body = parsedJSON("""
                {"searchText":"$searchString","skipResults":0,"takeResults":50,"sortOptions":[],"summarizedHitCountsNeeded":true,"searchFilters":{}}
            """.trimIndent())
        )

        val response = client.execute(request)

        return parsedJSON(response.body.toStringLiteral()) as JSONObjectValue
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

        val configJson = SpecmaticConfigJson.load()

        val azureAuthToken = PersonalAccessToken(getPersonalAccessToken() ?: throw ContractException("Access token not found, put it in ${System.getProperty("user.home")}/specmatic.json"))

        val repository = configJson.repository
            ?: exitWithMessage("""specmatic.json needs to contain a the repository information, as below:
                    |{
                    |  "repository": {
                    |    "provider": "azure"
                    |    "collectionName": "NameOfTheCollectionContainingThisProject"
                    |  }
                    |}
                """.trimMargin())

        val collection = repository.collectionName
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