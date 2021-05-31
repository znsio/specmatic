package `in`.specmatic.conversions

import `in`.specmatic.core.*
import `in`.specmatic.core.pattern.*
import io.cucumber.messages.Messages
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.media.*
import io.swagger.v3.oas.models.parameters.HeaderParameter
import io.swagger.v3.oas.models.parameters.PathParameter
import io.swagger.v3.oas.models.parameters.QueryParameter
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

    override fun validateCompliance(scenarioInfo: ScenarioInfo, steps: List<Messages.GherkinDocument.Feature.Step>) {
        validateScenarioInfoCompliance(openApitoScenarioInfos(), steps, scenarioInfo)
    }

    private fun openApitoScenarioInfos(): List<ScenarioInfo> {
        return openApi.paths.map { (openApiPath, pathItem) ->
            openApiOperations(pathItem).map { (httpMethod, operation) ->
                val specmaticPath = toSpecmaticPath(openApiPath, operation)

                toHttpResponsePatterns(operation.responses).map { (response, responseMediaType, httpResponsePattern) ->
                    toHttpRequestPatterns(
                        specmaticPath,
                        httpMethod,
                        operation
                    ).map { httpRequestPattern: HttpRequestPattern ->
                        val scenarioName =
                            """Open API - Operation Summary: ${operation.summary}. Response: ${response.description}"""
                        scenarioInfo(scenarioName, httpRequestPattern, httpResponsePattern)
                    }
                }.flatten()
            }.flatten()
        }.flatten()
    }

    private fun toScenarioInfosWithExamples(): List<ScenarioInfo> {
        return openApi.paths.map { (openApiPath, pathItem) ->
            openApiOperations(pathItem).map { (httpMethod, operation) ->
                val specmaticPath = toSpecmaticPath(openApiPath, operation)

                toHttpResponsePatterns(operation.responses).map { (response, responseMediaType, httpResponsePattern) ->
                    val responseExamples = responseMediaType.examples.orEmpty()
                    val specmaticExampleRows: List<Row> = responseExamples.map { (exampleName, value) ->
                        val requestExamples =
                            operation.parameters.filter { parameter -> parameter.examples.any { it.key == exampleName } }
                                .map { it.name to it.examples[exampleName]!!.value }.toMap()

                        requestExamples.map { (key, value) -> key to value.toString() }.toList().isNotEmpty()

                        when {
                            requestExamples.isNotEmpty() -> Row(
                                requestExamples.keys.toList(),
                                requestExamples.values.toList().map { it.toString() })
                            else -> Row()
                        }
                    }

                    specmaticExampleRows.map { specmaticExampleRow: Row ->
                        toHttpRequestPatterns(
                            specmaticPath,
                            httpMethod,
                            operation
                        ).map { httpRequestPattern: HttpRequestPattern ->
                            val scenarioName =
                                """Open API - Operation Summary: ${operation.summary}. Response: ${response.description} Examples: $specmaticExampleRow"""
                            scenarioInfo(scenarioName, httpRequestPattern, httpResponsePattern, specmaticExampleRow)
                        }
                    }.flatten()
                }.flatten()
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
        httpRequestPattern = when (specmaticExampleRow.columnNames.isEmpty()) {
            true -> httpRequestPattern
            else -> httpRequestPattern.newBasedOn(specmaticExampleRow, Resolver())[0]
        },
        httpResponsePattern = when (specmaticExampleRow.columnNames.isEmpty()) {
            true -> httpResponsePattern
            else -> httpResponsePattern.newBasedOn(specmaticExampleRow, Resolver())[0]
        }
    )

    private fun toHttpResponsePatterns(responses: ApiResponses?): List<Triple<ApiResponse, MediaType, HttpResponsePattern>> {
        return responses.orEmpty().map { (status, response) ->
            val headersMap = response.headers.orEmpty().map { (headerName, header) ->
                toSpecmaticParamName(header.required != true, headerName) to toSpecmaticPattern(header.schema)
            }.toMap()
            when (val content = response.content) {
                null -> listOf(
                    Triple(
                        response, MediaType(), HttpResponsePattern(
                            headersPattern = HttpHeadersPattern(headersMap),
                            status = when (status) {
                                "default" -> 400
                                else -> status.toInt()
                            }
                        )
                    )
                )
                else -> content.map { (contentType, mediaType) ->
                    Triple(
                        response, mediaType, HttpResponsePattern(
                            headersPattern = HttpHeadersPattern(
                                headersMap.plus(
                                    toPatternPair(
                                        "Content-Type",
                                        contentType
                                    )
                                )
                            ),
                            status = when (status) {
                                "default" -> 400
                                else -> status.toInt()
                            },
                            body = toSpecmaticPattern(mediaType)
                        )
                    )
                }
            }
        }.flatten()
    }

    fun toHttpRequestPatterns(path: String, httpMethod: String, operation: Operation): List<HttpRequestPattern> {

        val parameters = operation.parameters

        val headersMap = parameters.orEmpty().filterIsInstance(HeaderParameter::class.java).map {
            toSpecmaticParamName(it.required != true, it.name) to toSpecmaticPattern(it.schema)
        }.toMap()

        return when (operation.requestBody) {
            null -> listOf(
                HttpRequestPattern(
                    urlMatcher = toURLMatcherWithOptionalQueryParams(path),
                    method = httpMethod,
                    headersPattern = HttpHeadersPattern(headersMap)
                )
            )
            else -> operation.requestBody.content.map { (contentType, mediaType) ->
                HttpRequestPattern(
                    urlMatcher = toURLMatcherWithOptionalQueryParams(path),
                    method = httpMethod,
                    headersPattern = HttpHeadersPattern(headersMap.plus(toPatternPair("Content-Type", contentType))),
                    body = toSpecmaticPattern(mediaType)
                )
            }
        }
    }

    fun toSpecmaticPattern(mediaType: MediaType): Pattern = toSpecmaticPattern(mediaType.schema)

    fun toSpecmaticPattern(schema: Schema<*>): Pattern = when (schema) {
        is StringSchema -> StringPattern
        is IntegerSchema -> NumberPattern
        is NumberSchema -> NumberPattern
        is UUIDSchema -> StringPattern
        is DateTimeSchema -> DateTimePattern
        is DateSchema -> StringPattern
        is BooleanSchema -> BooleanPattern
        is ObjectSchema -> {
            val requiredFields = schema.required.orEmpty()
            val schemaProperties = schema.properties.map { (propertyName, propertyType) ->
                val optional = !requiredFields.contains(propertyName)
                toSpecmaticParamName(optional, propertyName) to toSpecmaticPattern(propertyType)
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

    private fun toSpecmaticParamName(optional: Boolean, name: String) = when (optional) {
        true -> "${name}?"
        false -> name
    }

    private fun resolveReference(component: String?): Pattern {
        return toSpecmaticPattern(openApi.components.schemas[component!!.removePrefix("#/components/schemas/")] as Schema<Any>)
    }

    private fun toSpecmaticPath(openApiPath: String, operation: Operation): String {
        var specmaticPath = openApiPath

        val parameters = operation.parameters

        parameters?.run {
            filterIsInstance(PathParameter::class.java).map {
                specmaticPath = specmaticPath.replace(
                    "{${it.name}}",
                    "(${it.name}:${toSpecmaticPattern(it.schema).typeName})"
                )
            }

            val queryParameters = parameters.filterIsInstance(QueryParameter::class.java).joinToString("&") {
                "${it.name}=${toSpecmaticPattern(it.schema)}"
            }

            if (queryParameters.isNotEmpty()) specmaticPath = "${specmaticPath}?${queryParameters}"
        }

        return specmaticPath
    }

    private fun openApiOperations(pathItem: PathItem): Map<String, Operation> =
        mapOf<String, Operation>(
            "GET" to pathItem.get,
            "POST" to pathItem.post,
            "DELETE" to pathItem.delete
        ).filter { (key, value) -> value != null }

}