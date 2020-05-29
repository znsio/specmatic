package run.qontract.conversions

import run.qontract.core.*
import run.qontract.core.pattern.ContractException
import run.qontract.core.pattern.parsedValue
import run.qontract.core.utilities.jsonStringToValueMap
import run.qontract.core.value.EmptyString
import run.qontract.core.value.JSONArrayValue
import run.qontract.core.value.JSONObjectValue
import run.qontract.core.value.Value
import run.qontract.mock.StubScenario
import run.qontract.nullLog
import run.qontract.test.HttpClient
import java.net.URI
import java.net.URL

fun postmanCollectionToGherkin(postmanContent: String): Pair<String, List<NamedStub>> {
    val items = postmanItems(postmanContent)

    val stubs = items.map { item ->
        val response = HttpClient(item.baseURL, nullLog).execute(item.request)
        NamedStub(item.name ?: "New scenario", StubScenario(item.request, response))
    }

    val gherkinString = toGherkinFeature(stubs)
    return Pair(gherkinString, stubs)
}

private fun postmanItems(postmanContent: String): List<PostmanItem> {
    val json = jsonStringToValueMap(postmanContent)
    val items = json.getValue("item") as JSONArrayValue

    return items.list.map { it as JSONObjectValue }.map { item ->
        val request = item.getJSONObjectValue("request")
        val method = request.getString("method")
        val url = toURL(request.jsonObject.getValue("url"))

        val baseURL = "${url.protocol}://${url.authority}"
        val query: Map<String, String> = url.query?.split("&")?.map { it.split("=").let { parts -> Pair(parts[0], parts[1]) } }?.fold(emptyMap()) { acc, entry -> acc.plus(entry) }
                ?: emptyMap()
        val headers: Map<String, String> = request.getJSONArray("header").map { it as JSONObjectValue }.fold(emptyMap()) { headers, header ->
            headers.plus(Pair(header.getString("name"), header.getString("value")))
        }

        val scenarioName = if(item.jsonObject.contains("name")) item.getString("name") else "New scenario"

        val (body, formFields, formData) = when {
            request.jsonObject.contains("body") -> when (val mode = request.getJSONObjectValue("body").getString("mode")) {
                "raw" -> Triple(parsedValue(request.getJSONObjectValue("body").getString(mode)), emptyMap<String, String>(), emptyList<MultiPartFormDataValue>())
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

                        MultiPartContentValue(name, parsedValue(value))
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

        PostmanItem(scenarioName, baseURL, HttpRequest(method, url.path, headers, body, query, formFields, formData))
    }
}

private fun toURL(urlData: Value): URL {
    return URI.create(when(urlData) {
        is JSONObjectValue -> urlData.jsonObject.getValue("raw").toStringValue()
        else -> urlData.toStringValue()
    }).toURL()
}
