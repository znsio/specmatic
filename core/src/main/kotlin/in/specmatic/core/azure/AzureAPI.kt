package `in`.specmatic.core.azure

import `in`.specmatic.core.HttpRequest
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.pattern.attempt
import `in`.specmatic.core.pattern.parsedJSON
import `in`.specmatic.core.value.JSONArrayValue
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.Value
import `in`.specmatic.test.HttpClient
import java.net.URI

private fun unDefault(jsonObject: JSONObjectValue, defaultCollectionName: String) =
    when (val collection = (jsonObject.jsonObject["collection"] ?: throw ContractException("Expected \"collection\" in Azure search result")).toStringLiteral()) {
        "DefaultCollection" -> defaultCollectionName
        else -> collection
    }

class AzureAPI(private val azureAuthToken: AzureAuthToken, private val azureBaseURL: String, private val collection: String) {
    data class ContractConsumerEntry(val collection: String, val project: String, val branch: String) {
        constructor(collection: String, referenceToSpecificationInAzureSearchResult: JSONObjectValue): this(
            unDefault(referenceToSpecificationInAzureSearchResult, collection),
            (referenceToSpecificationInAzureSearchResult.jsonObject["project"]
                ?: throw ContractException("Expected \"project\" in consumer search result")).toStringLiteral(),
            (referenceToSpecificationInAzureSearchResult.jsonObject["branch"]
                ?: throw ContractException("Expected \"branch\" in consumer search result")).toStringLiteral()
        )
        constructor(collection: String, referenceToSpecificationInAzureSearchResult: Value): this(collection, referenceToSpecificationInAzureSearchResult as JSONObjectValue)

        val description: String
          get() {
              return "${collection}/${project} (${branch})"
          }
    }

    fun referencesToContract(searchString: String): List<ContractConsumerEntry> {
        val azureResponse = codeAdvancedSearch(searchString)
        val referencesToSpecification: JSONArrayValue = azureResponse.findFirstChildByPath("results.values") as JSONArrayValue

        return attempt("Error processing Azure's response to a search for a specification ($searchString)") {
            referencesToSpecification.list.map { referenceToSpecification ->
                ContractConsumerEntry(collection, referenceToSpecification)
            }
        }
    }

    private fun codeAdvancedSearch(searchString: String): JSONObjectValue {
        val client = HttpClient(azureBaseURL, log = { })
        val request = HttpRequest(
            "POST",
            URI("/$collection/_apis/search/codeAdvancedQueryResults?api-version=5.1-preview.1")
        ).copy(
            headers = mapOf(
                "Authorization" to "Basic ${azureAuthToken.basic()}"
            ),
            body = parsedJSON(
                """
                {"searchText":"$searchString","skipResults":0,"takeResults":50,"sortOptions":[],"summarizedHitCountsNeeded":true,"searchFilters":{}}
            """.trimIndent()
            )
        )

        val response = client.execute(request)

        return parsedJSON(response.body.toStringLiteral()) as JSONObjectValue
    }

}