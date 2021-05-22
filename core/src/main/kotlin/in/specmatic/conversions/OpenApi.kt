package `in`.specmatic.conversions

import `in`.specmatic.core.*
import `in`.specmatic.core.pattern.*
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.media.IntegerSchema
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import io.swagger.v3.parser.OpenAPIV3Parser

fun toScenarioInfos(openApiFile: String): List<ScenarioInfo> {
    val openApi = OpenAPIV3Parser().read(openApiFile)
    return openApi.paths.map { (openApiPath, pathItem) ->
        val get = pathItem.get
        var specmaticPath = openApiPath
        get.parameters.filter { it.`in` == "path" }.map { specmaticPath = specmaticPath.replace("{${it.name}}", "(${it.name}:${toSpecmaticPattern(it.schema).typeName})") }
        toHttpResponsePattern(get.responses).map { (response, responseMediaType, httpResponsePattern) ->
            ScenarioInfo(scenarioName = "Request: " + get.summary + " Response: " + response.description,
                    httpRequestPattern = httpRequestPattern(specmaticPath, get),
                    httpResponsePattern = httpResponsePattern
            )
        }
    }.flatten()
}

fun toScenarioInfosWithExamples(openApiFile: String): List<ScenarioInfo> {
    val openApi = OpenAPIV3Parser().read(openApiFile)
    return openApi.paths.map { (openApiPath, pathItem) ->
        val get = pathItem.get
        var specmaticPath = openApiPath
        get.parameters.filter { it.`in` == "path" }.map { specmaticPath = specmaticPath.replace("{${it.name}}", "(${it.name}:${toSpecmaticPattern(it.schema).typeName})") }
        toHttpResponsePattern(get.responses).map { (response, responseMediaType, httpResponsePattern) ->
            if (!responseMediaType.examples.isNullOrEmpty()) {
                responseMediaType.examples.map { (exampleName, value) ->
                    val requestExamples = get.parameters.filter { parameter -> parameter.examples.any { it.key == exampleName } }.map { it.name to it.examples[exampleName]!!.value }.toMap()

                    val httpRequestPattern = if (!requestExamples.isEmpty()) {
                        httpRequestPattern(specmaticPath, get).newBasedOn(Row(requestExamples.keys.toList(), requestExamples.values.toList().map { it.toString() }), Resolver())[0]
                    } else {
                        httpRequestPattern(specmaticPath, get)
                    }
                    ScenarioInfo(scenarioName = "Request: " + get.summary + " Response: " + response.description,
                            httpRequestPattern = httpRequestPattern,
                            httpResponsePattern = httpResponsePattern
                    )
                }
            } else {
                listOf()
            }
        }.flatten()
    }.flatten()
}


fun toHttpResponsePattern(responses: ApiResponses?): List<Triple<ApiResponse, MediaType, HttpResponsePattern>> {
    return responses!!.map { (status, response) ->
        response.content.map { (contentType, mediaType) ->
            Triple(response, mediaType, HttpResponsePattern(headersPattern = HttpHeadersPattern(mapOf(toPatternPair("Content-Type", contentType))),
                    status = status.toInt(),
                    body = toSpecmaticPattern(mediaType)
            ))
        }
    }.flatten()
}

fun toSpecmaticPattern(mediaType: MediaType): Pattern = toSpecmaticPattern(mediaType.schema)

fun toSpecmaticPattern(schema: Schema<Any>): Pattern = when (schema) {
    is StringSchema -> StringPattern
    is IntegerSchema -> NumberPattern
    else -> NullPattern
}

fun httpRequestPattern(path: String, operation: Operation): HttpRequestPattern {
    return HttpRequestPattern(urlMatcher = toURLMatcherWithOptionalQueryParams(path), method = "GET")
}
