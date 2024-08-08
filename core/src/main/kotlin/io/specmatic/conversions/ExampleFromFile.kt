package io.specmatic.conversions

import io.specmatic.core.HttpResponse
import io.specmatic.core.SpecmaticConfig
import io.specmatic.core.log.logger
import io.specmatic.core.pattern.*
import io.specmatic.core.utilities.URIUtils.parseQuery
import io.specmatic.core.value.EmptyString
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.Value
import io.specmatic.mock.mockFromJSON
import io.specmatic.stub.httpResponseLog
import java.io.File
import java.net.URI

class ExampleFromFile(val json: JSONObjectValue, val file: File) {
    fun toRow(specmaticConfig: SpecmaticConfig = SpecmaticConfig()): Row {
        logger.log("Loading test file ${this.expectationFilePath}")

        val examples: Map<String, String> =
            headers
                .plus(queryParams)
                .plus(requestBody?.let { mapOf("(REQUEST-BODY)" to it.toStringLiteral()) } ?: emptyMap())

        val (
            columnNames,
            values
        ) = examples.entries.let { entry ->
            entry.map { it.key } to entry.map { it.value }
        }

        val responseExample: ResponseExample? = response?.let { httpResponse ->
            when {
                specmaticConfig.isResponseValueValidationEnabled() ->
                    ResponseValueExample(httpResponse)

                else ->
                    null
            }

        }

        val mockFromJSON = mockFromJSON(json.jsonObject)

        return Row(
            columnNames,
            values,
            name = testName,
            fileSource = this.file.canonicalPath,
            responseExampleForValidation = responseExample,
            requestExample = mockFromJSON.request,
            responseExample = response
        )
    }

    constructor(file: File) : this(parsedJSONObject(file.readText()), file)

    val expectationFilePath: String = file.canonicalPath

    val response: HttpResponse?
        get() {
            if(responseBody == null && responseHeaders == null)
                return null

            val body = responseBody ?: EmptyString
            val headers = responseHeaders ?: JSONObjectValue()

            return HttpResponse(responseStatus, headers.jsonObject.mapValues { it.value.toStringLiteral() }, body)
        }

    val responseBody: Value? = attempt("Error reading response body in file ${file.parentFile.canonicalPath}") {
        json.findFirstChildByPath("http-response.body")
    }

    val responseHeaders: JSONObjectValue? = attempt("Error reading response headers in file ${file.parentFile.canonicalPath}") {
        val headers = json.findFirstChildByPath("http-response.headers")
        if(headers == null)
            return@attempt null

        if(headers !is JSONObjectValue)
            throw ContractException("http-response.headers should be a JSON object, but instead it was ${headers.toStringLiteral()}")

        headers
    }

    val responseStatus: Int = attempt("Error reading status in file ${file.parentFile.canonicalPath}") {
        json.findFirstChildByPath("http-response.status")?.toStringLiteral()?.toInt()
    } ?: throw ContractException("Response status code was not found.")

    val requestMethod: String = attempt("Error reading method in file ${file.parentFile.canonicalPath}") {
        json.findFirstChildByPath("http-request.method")?.toStringLiteral()
    } ?: throw ContractException("Request method was not found.")

    val requestPath: String = attempt("Error reading path in file ${file.parentFile.canonicalPath}") {
        json.findFirstChildByPath("http-request.path")?.toStringLiteral()
    } ?: throw ContractException("Request path was not found.")

    val testName: String = attempt("Error reading expectation name in file ${file.parentFile.canonicalPath}") {
        json.findFirstChildByPath("name")?.toStringLiteral() ?: file.nameWithoutExtension
    }

    val queryParams: Map<String, String>
        get() {
            val uri = URI.create(requestPath)
            val queryParamsFromURL = parseQuery(uri.query)

            val queryParamsFromJSONBlock = attempt("Error reading query params in file ${file.parentFile.canonicalPath}") {
                (json.findFirstChildByPath("http-request.query") as JSONObjectValue?)?.jsonObject?.mapValues { (_, value) ->
                    value.toStringLiteral()
                } ?: emptyMap()
            }

            return queryParamsFromURL + queryParamsFromJSONBlock
        }

    val headers: Map<String, String> = attempt("Error reading headers in file ${file.parentFile.canonicalPath}") {
        (json.findFirstChildByPath("http-request.headers") as JSONObjectValue?)?.jsonObject?.mapValues { (_, value) ->
            value.toStringLiteral()
        } ?: emptyMap()
    }

    val requestBody: Value? = attempt("Error reading request body in file ${file.parentFile.canonicalPath}") {
        json.findFirstChildByPath("http-request.body")
    }
}
