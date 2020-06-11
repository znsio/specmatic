package run.qontract.conversions

import run.qontract.core.*
import run.qontract.core.pattern.*
import run.qontract.core.utilities.jsonStringToValueMap
import run.qontract.core.utilities.parseXML
import run.qontract.core.value.*
import run.qontract.mock.ScenarioStub
import run.qontract.nullLog
import run.qontract.test.HttpClient
import java.net.URI
import java.net.URL

fun hostAndPort(uri: String): String {
    return try {
        val uri = URI.create(uri)
        val port: String = if(uri.port > 0) ":${uri.port}" else ""
        "${uri.host}$port"
    } catch(e: Throwable) {
        ""
    }
}

fun postmanCollectionToGherkin(postmanContent: String): List<Triple<String, String, List<NamedStub>>> {
    val postmanCollection = stubsFromPostmanCollection(postmanContent)

    return when {
        postmanCollection.stubs.isNotEmpty() -> {
            val groups = postmanCollection.stubs.groupBy { hostAndPort(it.first) }

            groups.entries.map {
                val collection = PostmanCollection(postmanCollection.name, it.value)
                val gherkinString = toGherkinFeature(collection)

                Triple(gherkinString, it.key, postmanCollection.stubs.map { it.second })
            }
        }
        else -> emptyList()
    }
}

fun toGherkinFeature(postmanCollection: PostmanCollection): String =
        toGherkinFeature(postmanCollection.name, postmanCollection.stubs.map { it.second })

data class PostmanCollection(val name: String, val stubs: List<Pair<String, NamedStub>>)

fun stubsFromPostmanCollection(postmanContent: String): PostmanCollection {
    val json = jsonStringToValueMap(postmanContent)
    val items = json.getValue("item") as JSONArrayValue

    val name = when {
        json.containsKey("info") && (json.getValue("info") as JSONObjectValue).jsonObject.containsKey("name") ->
            (json.getValue("info") as JSONObjectValue).getString("name")
        else -> "New Feature"
    }
    
    return PostmanCollection(name, items.list.map { it as JSONObjectValue }.map { item ->
        postmanItemToStubs(item)
    }.flatten())
}

private fun postmanItemToStubs(item: JSONObjectValue): List<Pair<String, NamedStub>> {
    if(!item.jsonObject.containsKey("request")) {
        val items = item.getJSONArray("item").map { it as JSONObjectValue }
        return items.flatMap { postmanItemToStubs(it) }
    }

    val request = item.getJSONObjectValue("request")
    val scenarioName = if (item.jsonObject.contains("name")) item.getString("name") else "New scenario"

    println("Getting response for $scenarioName")

    return try {
        val responses = item.getJSONArray("response")
        val namedStubsFromSavedResponses = namedStubsFromPostmanResponses(responses)

        baseNamedStub(request, scenarioName).plus(namedStubsFromSavedResponses)
    } catch (e: Throwable) {
        println("  Exception thrown when processing Postman scenario \"$scenarioName\": ${e.localizedMessage ?: e.message ?: e.javaClass.name}")
        emptyList()
    }
}

private fun baseNamedStub(request: JSONObjectValue, scenarioName: String): List<Pair<String, NamedStub>> {
    return try {
        val (baseURL, httpRequest) = postmanItemRequest(request)

        println("Using base url $baseURL")
        val response = HttpClient(baseURL, nullLog).execute(httpRequest)

        listOf(Pair(baseURL, NamedStub(scenarioName, ScenarioStub(httpRequest, response))))
    } catch (e: Throwable) {
        println("  Failed to generate a response for the Postman request.")
        emptyList()
    }
}

fun namedStubsFromPostmanResponses(responses: List<Value>): List<Pair<String, NamedStub>> {
    return responses.map {
        val responseItem = it as JSONObjectValue

        val scenarioName = if (responseItem.jsonObject.contains("name")) responseItem.getString("name") else "New scenario"
        val innerRequest = responseItem.getJSONObjectValue("originalRequest")

        val (baseURL, innerHttpRequest) = postmanItemRequest(innerRequest)
        val innerHttpResponse: HttpResponse = postmanItemResponse(responseItem)

        Pair(baseURL, NamedStub(scenarioName, ScenarioStub(innerHttpRequest, innerHttpResponse)))
    }
}

fun postmanItemResponse(responseItem: JSONObjectValue): HttpResponse {
    val status = responseItem.getInt("code")

    val headers: Map<String, String> = when {
        responseItem.jsonObject.containsKey("header") -> {
            val rawHeaders = responseItem.jsonObject.getValue("header") as JSONArrayValue
            emptyMap<String, String>().plus(rawHeaders.list.map {
                val rawHeader = it as JSONObjectValue

                val name = rawHeader.getString("key")
                val value = rawHeader.getString("value")

                Pair(name, value)
            })
        }
        else -> emptyMap()
    }

    val body: Value = when {
        responseItem.jsonObject.containsKey("body") -> guessType(parsedValue(responseItem.jsonObject.getValue("body").toString()))
        else -> EmptyString
    }

    return HttpResponse(status, headers, body)
}

fun postmanItemRequest(request: JSONObjectValue): Pair<String, HttpRequest> {
    val method = request.getString("method")
    val url = toURL(request.jsonObject.getValue("url"))

    val baseURL = "${url.protocol}://${url.authority}"
    val query: Map<String, String> = url.query?.split("&")?.map { it.split("=").let { parts -> Pair(parts[0], parts[1]) } }?.fold(emptyMap()) { acc, entry -> acc.plus(entry) }
            ?: emptyMap()
    val headers: Map<String, String> = request.getJSONArray("header").map { it as JSONObjectValue }.fold(emptyMap()) { headers, header ->
        headers.plus(Pair(header.getString("key"), header.getString("value")))
    }

    val (body, formFields, formData) = when {
        request.jsonObject.contains("body") -> when (val mode = request.getJSONObjectValue("body").getString("mode")) {
            "raw" -> Triple(guessType(parsedValue(request.getJSONObjectValue("body").getString(mode))), emptyMap<String, String>(), emptyList<MultiPartFormDataValue>())
            "urlencoded" -> {
                val rawFormFields = request.getJSONObjectValue("body").getJSONArray(mode)
                val formFields = rawFormFields.map {
                    val formField = it as JSONObjectValue
                    val name = formField.getString("key")
                    val value = formField.getString("value")

                    Pair(name, value)
                }.fold(emptyMap<String, String>()) { acc, entry -> acc.plus(entry) }

                Triple(EmptyString, formFields, emptyList())
            }
            "formdata" -> {
                val rawFormData = request.getJSONObjectValue("body").getJSONArray(mode)
                val formData = rawFormData.map {
                    val formField = it as JSONObjectValue
                    val name = formField.getString("key")
                    val value = formField.getString("value")

                    MultiPartContentValue(name, guessType(parsedValue(value)))
                }

                Triple(EmptyString, emptyMap(), formData)
            }
            "file" -> {
                throw ContractException("File mode is NOT supported yet.")
            }
            else -> Triple(EmptyString, emptyMap(), emptyList())
        }
        else -> Triple(EmptyString, emptyMap(), emptyList())
    }

    val httpRequest = HttpRequest(method, url.path, headers, body, query, formFields, formData)
    return Pair(baseURL, httpRequest)
}

private fun toURL(urlData: Value): URL {
    return URI.create(when(urlData) {
        is JSONObjectValue -> urlData.jsonObject.getValue("raw").toStringValue().trim()
        else -> urlData.toStringValue().trim()
    }).toURL()
}

fun guessType(value: Value): Value = when(value) {
    is StringValue -> try {
        when {
            isNumber(value) -> NumberValue(convertToNumber(value.string))
            value.string.toLowerCase() in listOf("true", "false") -> BooleanValue(value.string.toLowerCase().toBoolean())
            value.string.startsWith("{") || value.string.startsWith("[") -> parsedJSONStructure(value.string)
            value.string.startsWith("<") -> XMLValue(parseXML(value.string))
            else -> value
        }
    } catch(e: Throwable) {
        value
    }
    else -> value
}
