package `in`.specmatic.conversions

import `in`.specmatic.core.*
import `in`.specmatic.core.pattern.*
import io.cucumber.messages.Messages
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.media.*
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import io.swagger.v3.parser.OpenAPIV3Parser

class OpenApiSpecification : IncludedSpecification {
    private val openApiFile: String
    private val openApi: OpenAPI

    constructor(openApiFile: String) {
        this.openApiFile = openApiFile
        openApi = OpenAPIV3Parser().read(openApiFile)
    }

    override fun toScenarioInfos(): List<ScenarioInfo> =
        openApitoScenarioInfos().filter { it.httpResponsePattern.status in 200..299 }
            .plus(toScenarioInfosWithExamples())

    private fun openApitoScenarioInfos(): List<ScenarioInfo> {
        return openApi.paths.map { (openApiPath, pathItem) ->
            val get = pathItem.get
            var specmaticPath = openApiPath

            get.parameters.filter { it.`in` == "path" }.map {
                specmaticPath =
                    specmaticPath.replace("{${it.name}}", "(${it.name}:${toSpecmaticPattern(it.schema).typeName})")
            }

            toHttpResponsePattern(get.responses).map { (response, _, httpResponsePattern) ->
                val httpRequestPattern = httpRequestPattern(specmaticPath, get)
                val scenarioName = "Request: " + get.summary + " Response: " + response.description
                scenarioInfo(scenarioName, httpRequestPattern, httpResponsePattern)
            }
        }.flatten()
    }

    private fun toScenarioInfosWithExamples(): List<ScenarioInfo> {
        return openApi.paths.map { (openApiPath, pathItem) ->
            val get = pathItem.get
            var specmaticPath = openApiPath
            get.parameters.filter { it.`in` == "path" }.map {
                specmaticPath =
                    specmaticPath.replace("{${it.name}}", "(${it.name}:${toSpecmaticPattern(it.schema).typeName})")
            }
            toHttpResponsePattern(get.responses).map { (response, responseMediaType, httpResponsePattern) ->
                val responseExamples = responseMediaType.examples.orEmpty()
                val specmaticExampleRows: List<Row> = responseExamples.map { (exampleName, value) ->
                    val requestExamples =
                        get.parameters.filter { parameter -> parameter.examples.any { it.key == exampleName } }
                            .map { it.name to it.examples[exampleName]!!.value }.toMap()

                    requestExamples.map { (key, value) -> key to value.toString() }.toList().isNotEmpty()

                    when {
                        requestExamples.isNotEmpty() -> Row(
                            requestExamples.keys.toList(),
                            requestExamples.values.toList().map { it.toString() })
                        else -> Row()
                    }
                }

                specmaticExampleRows.map {
                    val httpRequestPattern = httpRequestPattern(specmaticPath, get)
                    val scenarioName = "Request: " + get.summary + " Response: " + response.description
                    scenarioInfo(scenarioName, httpRequestPattern, httpResponsePattern, it)
                }
            }.flatten()
        }.flatten()
    }

    private fun scenarioInfo(
        scenarioName: String,
        httpRequestPattern: HttpRequestPattern,
        httpResponsePattern: HttpResponsePattern,
        specmaticExampleRow: Row = Row()
    ) = ScenarioInfo(
        scenarioName = scenarioName,
        httpRequestPattern = httpRequestPattern.newBasedOn(specmaticExampleRow, Resolver())[0],
        httpResponsePattern = httpResponsePattern.newBasedOn(specmaticExampleRow, Resolver())[0]
    )

    private fun toHttpResponsePattern(responses: ApiResponses?): List<Triple<ApiResponse, MediaType, HttpResponsePattern>> {
        return responses!!.map { (status, response) ->
            response.content.map { (contentType, mediaType) ->
                Triple(
                    response, mediaType, HttpResponsePattern(
                        headersPattern = HttpHeadersPattern(mapOf(toPatternPair("Content-Type", contentType))),
                        status = when (status) {
                            "default" -> 400
                            else -> status.toInt()
                        },
                        body = toSpecmaticPattern(mediaType)
                    )
                )
            }
        }.flatten()
    }

    fun toSpecmaticPattern(mediaType: MediaType) = toSpecmaticPattern(mediaType.schema)

    fun toSpecmaticPattern(schema: Schema<*>): Pattern = when (schema) {
        is StringSchema -> StringPattern
        is IntegerSchema -> NumberPattern
        is ObjectSchema -> {
            val schemaProperties = schema.properties.map { (name, type) ->
                name to toSpecmaticPattern(type)
            }.toMap()
            toJSONObjectPattern(schemaProperties)
        }
        is ArraySchema -> {
            JSONArrayPattern(listOf(toSpecmaticPattern(schema.items)))
        }
        is ComposedSchema -> {
            NullPattern
        }
        is Schema -> {
            resolveReference(schema.`$ref`)
        }
        else -> NullPattern
    }

    private fun resolveReference(component: String?): Pattern {
        return toSpecmaticPattern(openApi.components.schemas[component!!.removePrefix("#/components/schemas/")] as Schema<Any>)
    }

    fun httpRequestPattern(path: String, operation: Operation): HttpRequestPattern {
        return HttpRequestPattern(urlMatcher = toURLMatcherWithOptionalQueryParams(path), method = "GET")
    }

    override fun validateCompliance(scenarioInfo: ScenarioInfo, steps: List<Messages.GherkinDocument.Feature.Step>) {
        validateScenarioInfoCompliance(openApitoScenarioInfos(), steps, scenarioInfo)
    }

}