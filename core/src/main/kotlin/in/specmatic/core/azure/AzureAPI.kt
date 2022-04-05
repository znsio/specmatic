package `in`.specmatic.core.azure

import `in`.specmatic.core.HttpRequest
import `in`.specmatic.core.pattern.parsedJSON
import `in`.specmatic.core.value.JSONArrayValue
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.Value
import `in`.specmatic.test.HttpClient
import java.net.URI

private fun undefault(jsonObject: JSONObjectValue, defaultCollectionName: String) =
    when (val collection = jsonObject.jsonObject["collection"]!!.toStringLiteral()) {
        "DefaultCollection" -> defaultCollectionName
        else -> collection
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