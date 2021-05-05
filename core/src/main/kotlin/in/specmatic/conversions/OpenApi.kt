package `in`.specmatic.conversions

import `in`.specmatic.core.*
import `in`.specmatic.core.pattern.NullPattern
import `in`.specmatic.core.pattern.Pattern
import `in`.specmatic.core.pattern.StringPattern
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.responses.ApiResponses
import io.swagger.v3.parser.OpenAPIV3Parser

fun toFeatures(openApiFile: String): List<Feature> {
    val openApi = OpenAPIV3Parser().read(openApiFile)
    val scenarioInfosMap = openApi.paths.map { (path, pathItem) ->
        val get = pathItem.get
        path to toHttpResponsePattern(get.responses).map {
            ScenarioInfo(scenarioName = get.summary,
                    httpRequestPattern = httpRequestPattern(path, get),
                    httpResponsePattern = it
            )
        }
    }.toMap()
    return scenarioInfosMap.map { (path, scenarioInfos) ->
        Feature(name = "test", scenarios = scenarioInfos.map {
            Scenario(it.scenarioName,
                    it.httpRequestPattern,
                    it.httpResponsePattern,
                    it.expectedServerState,
                    it.examples,
                    it.patterns,
                    it.fixtures,
                    it.kafkaMessage,
                    it.ignoreFailure,
                    it.references,
                    it.bindings)
        }.toList())
    }
}

fun toHttpResponsePattern(responses: ApiResponses?): List<HttpResponsePattern> {
    return responses!!.map { (status, response) ->
        response.content.map { (contentType, mediaType) ->
            HttpResponsePattern(headersPattern = HttpHeadersPattern(mapOf(toPatternPair("Content-Type", contentType))),
                    status = status.toInt(),
                    body = toSpecmaticPattern(mediaType)
            )
        }
    }.flatten()
}

fun toSpecmaticPattern(mediaType: MediaType): Pattern = when (mediaType.schema) {
    is StringSchema -> StringPattern
    else -> NullPattern
}

fun httpRequestPattern(path: String, operation: Operation): HttpRequestPattern {
    return HttpRequestPattern(urlMatcher = toURLMatcherWithOptionalQueryParams(path), method = "GET")
}
