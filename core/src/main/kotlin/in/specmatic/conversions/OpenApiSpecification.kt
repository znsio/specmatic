package `in`.specmatic.conversions

import `in`.specmatic.core.*
import `in`.specmatic.core.Result.Failure
import `in`.specmatic.core.git.information
import `in`.specmatic.core.pattern.*
import `in`.specmatic.core.value.NumberValue
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.core.value.Value
import io.cucumber.messages.types.Step
import io.ktor.util.reflect.*
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
import java.io.File

class OpenApiSpecification(private val openApiFile: String, private val openApi: OpenAPI) : IncludedSpecification {
    companion object {
        fun fromFile(openApiFile: String): OpenApiSpecification {
            val openApi = OpenAPIV3Parser().read(openApiFile)
            return OpenApiSpecification(openApiFile, openApi)
        }

        fun fromYAML(yamlContent: String, filePath: String): OpenApiSpecification {
            val openApi = OpenAPIV3Parser().readContents(yamlContent).openAPI
            return OpenApiSpecification(filePath, openApi)
        }
    }

    val patterns = mutableMapOf<String, Pattern>()

    fun toFeature(): Feature {
        val name = File(openApiFile).name
        return Feature(toScenarioInfos().map { Scenario(it) }, name = name)
    }

    override fun toScenarioInfos(): List<ScenarioInfo> =
        openApitoScenarioInfos().filter { it.httpResponsePattern.status in 200..299 }
            .plus(toScenarioInfosWithExamples())

    override fun matches(
        specmaticScenarioInfo: ScenarioInfo,
        steps: List<Step>
    ): List<ScenarioInfo> {
        val openApiScenarioInfos = openApitoScenarioInfos()
        if (openApiScenarioInfos.isNullOrEmpty() || !steps.isNotEmpty()) return listOf(specmaticScenarioInfo)
        val result: MatchingResult<Pair<ScenarioInfo, List<ScenarioInfo>>> =
            specmaticScenarioInfo to openApiScenarioInfos to
                    ::matchesPath then
                    ::matchesMethod then
                    ::matchesStatus then
                    ::updateUrlMatcher otherwise
                    ::handleError
        when (result) {
            is MatchFailure -> throw ContractException(result.error.message)
            is MatchSuccess -> return result.value.second
        }
    }

    private fun matchesPath(parameters: Pair<ScenarioInfo, List<ScenarioInfo>>): MatchingResult<Pair<ScenarioInfo, List<ScenarioInfo>>> {
        val (specmaticScenarioInfo, openApiScenarioInfos) = parameters

        val matchingScenarioInfos = openApiScenarioInfos.filter {
            it.httpRequestPattern.urlMatcher!!.matches(
                specmaticScenarioInfo.httpRequestPattern.generate(
                    Resolver()
                ), Resolver()
            ).isTrue()
        }

        return when {
            matchingScenarioInfos.isEmpty() -> MatchFailure(
                Failure(
                    """Scenario: "${specmaticScenarioInfo.scenarioName}" PATH: "${
                        specmaticScenarioInfo.httpRequestPattern.urlMatcher!!.generatePath(Resolver())
                    }" is not as per included wsdl / OpenApi spec"""
                )
            )
            else -> MatchSuccess(specmaticScenarioInfo to matchingScenarioInfos)
        }
    }

    private fun matchesMethod(parameters: Pair<ScenarioInfo, List<ScenarioInfo>>): MatchingResult<Pair<ScenarioInfo, List<ScenarioInfo>>> {
        val (specmaticScenarioInfo, openApiScenarioInfos) = parameters

        val matchingScenarioInfos =
            openApiScenarioInfos.filter { it.httpRequestPattern.method == specmaticScenarioInfo.httpRequestPattern.method }

        return when {
            matchingScenarioInfos.isEmpty() -> MatchFailure(
                Failure(
                    """Scenario: "${specmaticScenarioInfo.scenarioName}" METHOD: "${
                        specmaticScenarioInfo.httpRequestPattern.method
                    }" is not as per included wsdl / OpenApi spec"""
                )
            )
            else -> MatchSuccess(specmaticScenarioInfo to matchingScenarioInfos)
        }
    }

    private fun matchesStatus(parameters: Pair<ScenarioInfo, List<ScenarioInfo>>): MatchingResult<Pair<ScenarioInfo, List<ScenarioInfo>>> {
        val (specmaticScenarioInfo, openApiScenarioInfos) = parameters

        val matchingScenarioInfos =
            openApiScenarioInfos.filter { it.httpResponsePattern.status == specmaticScenarioInfo.httpResponsePattern.status }

        return when {
            matchingScenarioInfos.isEmpty() -> MatchFailure(
                Failure(
                    """Scenario: "${specmaticScenarioInfo.scenarioName}" RESPONSE STATUS: "${
                        specmaticScenarioInfo.httpResponsePattern.status
                    }" is not as per included wsdl / OpenApi spec"""
                )
            )
            else -> MatchSuccess(specmaticScenarioInfo to matchingScenarioInfos)
        }
    }

    private fun updateUrlMatcher(parameters: Pair<ScenarioInfo, List<ScenarioInfo>>): MatchingResult<Pair<ScenarioInfo, List<ScenarioInfo>>> {
        val (specmaticScenarioInfo, openApiScenarioInfos) = parameters

        return MatchSuccess(specmaticScenarioInfo to openApiScenarioInfos.map {
            it.copy(httpRequestPattern = it.httpRequestPattern.copy(urlMatcher = specmaticScenarioInfo.httpRequestPattern.urlMatcher))
        })
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
                        scenarioInfo(scenarioName, httpRequestPattern, httpResponsePattern, patterns = this.patterns)
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
                            scenarioInfo(
                                scenarioName,
                                httpRequestPattern,
                                httpResponsePattern,
                                specmaticExampleRow,
                                emptyMap()
                            )
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
        specmaticExampleRow: Row = Row(),
        patterns: Map<String, Pattern>
    ) = ScenarioInfo(
        scenarioName = scenarioName,
        patterns = patterns,
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

    fun toSpecmaticPattern(
        schema: Schema<*>,
        typeStack: List<String> = emptyList(),
        patternName: String = ""
    ): Pattern {
        val pattern = when (schema) {
            is StringSchema -> when (schema.enum) {
                null -> StringPattern(minLength = schema.minLength, maxLength = schema.maxLength)
                else -> toEnum(schema) { enumValue -> StringValue(enumValue.toString()) }
            }
            is IntegerSchema -> when (schema.enum) {
                null -> NumberPattern()
                else -> toEnum(schema) { enumValue -> NumberValue(enumValue.toString().toInt()) }
            }
            is NumberSchema -> NumberPattern()
            is UUIDSchema -> StringPattern()
            is DateTimeSchema -> DateTimePattern
            is DateSchema -> StringPattern()
            is BooleanSchema -> BooleanPattern
            is ObjectSchema -> {
                val requiredFields = schema.required.orEmpty()
                val schemaProperties = schema.properties.map { (propertyName, propertyType) ->
                    val optional = !requiredFields.contains(propertyName)
                    if (patternName.isNotEmpty()) {
                        if (typeStack.contains(patternName)) toSpecmaticParamName(
                            optional,
                            propertyName
                        ) to DeferredPattern("(${patternName})")
                        else {
                            val specmaticPattern = toSpecmaticPattern(
                                propertyType,
                                typeStack.plus(patternName),
                                patternName
                            )
                            toSpecmaticParamName(optional, propertyName) to specmaticPattern
                        }
                    } else {
                        toSpecmaticParamName(optional, propertyName) to toSpecmaticPattern(
                            propertyType,
                            typeStack
                        )
                    }
                }.toMap()
                val jsonObjectPattern = toJSONObjectPattern(schemaProperties, "(${patternName})")
                patterns["(${patternName})"] = jsonObjectPattern
                jsonObjectPattern
            }
            is ArraySchema -> {
                JSONArrayPattern(listOf(toSpecmaticPattern(schema.items)))
            }
            is ComposedSchema -> {
                throw UnsupportedOperationException("Specmatic does not support oneOf, allOf and anyOf")
            }
            is Schema -> {
                val component = schema.`$ref`
                    ?: throw ContractException("Found null \$ref property in schema ${schema.name ?: "which had no name"}").also {
                        information.forDebugging("Schema:")
                        information.forDebugging(schema.toString().prependIndent("  "))
                    }
                val (componentName, referredSchema) = resolveReferenceToSchema(component)
                if (patternName.isNotEmpty()
                    && patternName == componentName
                    && typeStack.contains(patternName)
                    && referredSchema.instanceOf(ObjectSchema::class)
                )
                    DeferredPattern("(${patternName})")
                else {
                    resolveReference(component)
                }
            }
            else -> throw UnsupportedOperationException("Specmatic is unable parse: $schema")
        }
        return when (schema.nullable != true) {
            true -> pattern
            else -> when (pattern) {
                is AnyPattern -> pattern
                else -> AnyPattern(listOf(NullPattern, pattern))
            }
        }
    }

    private fun toEnum(schema: Schema<*>, toSpecmaticValue: (Any) -> Value) =
        AnyPattern(schema.enum.map<Any, Pattern> { enumValue ->
            when (enumValue) {
                null -> NullPattern
                else -> ExactValuePattern(toSpecmaticValue(enumValue))
            }
        }.toList())

    private fun toSpecmaticParamName(optional: Boolean, name: String) = when (optional) {
        true -> "${name}?"
        false -> name
    }

    private fun resolveReference(component: String): Pattern {
        val (componentName, referredSchema) = resolveReferenceToSchema(component)
        return toSpecmaticPattern(referredSchema, patternName = componentName)
    }

    private fun resolveReferenceToSchema(component: String): Pair<String, Schema<Any>> {
        if (!component.startsWith("#")) throw UnsupportedOperationException("Specmatic only supports local component references.")
        val componentName = component!!.removePrefix("#/components/schemas/")
        return componentName to openApi.components.schemas[componentName] as Schema<Any>
    }

    private fun toSpecmaticPath(openApiPath: String, operation: Operation): String {
        val parameters = operation.parameters ?: return openApiPath

        var specmaticPath = openApiPath
        parameters.filterIsInstance(PathParameter::class.java).map {
            specmaticPath = specmaticPath.replace(
                "{${it.name}}",
                "(${it.name}:${toSpecmaticPattern(it.schema).typeName})"
            )
        }

        val queryParameters = parameters.filterIsInstance(QueryParameter::class.java).joinToString("&") {
            "${it.name}=${toSpecmaticPattern(it.schema)}"
        }

        if (queryParameters.isNotEmpty()) specmaticPath = "${specmaticPath}?${queryParameters}"

        return specmaticPath
    }

    private fun openApiOperations(pathItem: PathItem): Map<String, Operation> =
        mapOf<String, Operation?>(
            "GET" to pathItem.get,
            "POST" to pathItem.post,
            "DELETE" to pathItem.delete,
            "PUT" to pathItem.put
        ).filter { (_, value) -> value != null }.map { (key, value) -> key to value!! }.toMap()
}