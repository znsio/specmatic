package `in`.specmatic.conversions

import `in`.specmatic.core.*
import `in`.specmatic.core.Result.Failure
import `in`.specmatic.core.log.LogStrategy
import `in`.specmatic.core.log.logger
import `in`.specmatic.core.pattern.*
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.NumberValue
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.core.value.Value
import `in`.specmatic.core.wsdl.parser.message.MULTIPLE_ATTRIBUTE_VALUE
import `in`.specmatic.core.wsdl.parser.message.OCCURS_ATTRIBUTE_NAME
import `in`.specmatic.core.wsdl.parser.message.OPTIONAL_ATTRIBUTE_VALUE
import io.cucumber.messages.internal.com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.messages.types.Step
import io.ktor.util.reflect.*
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.examples.Example
import io.swagger.v3.oas.models.media.*
import io.swagger.v3.oas.models.parameters.*
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.parser.OpenAPIV3Parser
import io.swagger.v3.parser.core.models.ParseOptions
import io.swagger.v3.parser.core.models.SwaggerParseResult
import java.io.File

private const val BEARER_SECURITY_SCHEME = "bearer"

class OpenApiSpecification(private val openApiFile: String, val openApi: OpenAPI) : IncludedSpecification,
    ApiSpecification {
    companion object {
        fun fromFile(openApiFilePath: String, relativeTo: String = ""): OpenApiSpecification {
            val openApiFile = File(openApiFilePath).let { openApiFile ->
                if (openApiFile.isAbsolute) {
                    openApiFile
                } else {
                    File(relativeTo).canonicalFile.parentFile.resolve(openApiFile)
                }
            }

            return fromFile(openApiFile.canonicalPath)
        }

        fun fromFile(openApiFile: String): OpenApiSpecification {
            val openApi = OpenAPIV3Parser().read(openApiFile, null, resolveExternalReferences())
            return OpenApiSpecification(openApiFile, openApi)
        }

        fun fromYAML(yamlContent: String, filePath: String, loggerForErrors: LogStrategy = logger): OpenApiSpecification {
            val parseResult: SwaggerParseResult =
                OpenAPIV3Parser().readContents(yamlContent, null, resolveExternalReferences(), filePath)
            val openApi: OpenAPI? = parseResult.openAPI

            if (openApi == null) {
                logger.debug("Failed to parse OpenAPI from file $filePath\n\n$yamlContent")

                printMessages(parseResult, filePath, loggerForErrors)

                throw ContractException("Could not parse contract $filePath, please validate the syntax using https://editor.swagger.io")
            } else if (parseResult.messages?.isNotEmpty() == true) {
                logger.log("The OpenAPI file $filePath was read successfully but with some issues")

                printMessages(parseResult, filePath, loggerForErrors)
            }

            return OpenApiSpecification(filePath, openApi)
        }

        private fun printMessages(parseResult: SwaggerParseResult, filePath: String, loggerForErrors: LogStrategy) {
            parseResult.messages.filterNotNull().let {
                if (it.isNotEmpty()) {
                    val parserMessages = parseResult.messages.joinToString(System.lineSeparator())
                    loggerForErrors.log("Error parsing file $filePath")
                    loggerForErrors.log(parserMessages.prependIndent("  "))
                }
            }
        }

        private fun resolveExternalReferences(): ParseOptions = ParseOptions().also { it.isResolve = true }
    }

    val patterns = mutableMapOf<String, Pattern>()

    fun isOpenAPI31(): Boolean {
        return openApi.openapi.startsWith("3.1")
    }

    fun toFeature(): Feature {
        val name = File(openApiFile).name
        return Feature(toScenarioInfos().map { Scenario(it) }, name = name, path = openApiFile)
    }

    override fun toScenarioInfos(): List<ScenarioInfo> {
        val scenarioInfosWithExamples = toScenarioInfosWithExamples()
        return openApitoScenarioInfos().filter { scenarioInfo ->
            scenarioInfosWithExamples.none { scenarioInfoWithExample ->
                scenarioInfoWithExample.matchesSignature(scenarioInfo)
            }
        }.plus(scenarioInfosWithExamples).filter { it.httpResponsePattern.status > 0 }
    }

    override fun matches(
        specmaticScenarioInfo: ScenarioInfo, steps: List<Step>
    ): List<ScenarioInfo> {
        val openApiScenarioInfos = openApitoScenarioInfos()
        if (openApiScenarioInfos.isNullOrEmpty() || !steps.isNotEmpty()) return listOf(specmaticScenarioInfo)
        val result: MatchingResult<Pair<ScenarioInfo, List<ScenarioInfo>>> =
            specmaticScenarioInfo to openApiScenarioInfos to ::matchesPath then ::matchesMethod then ::matchesStatus then ::updateUrlMatcher otherwise ::handleError
        when (result) {
            is MatchFailure -> throw ContractException(result.error.message)
            is MatchSuccess -> return result.value.second
        }
    }

    private fun matchesPath(parameters: Pair<ScenarioInfo, List<ScenarioInfo>>): MatchingResult<Pair<ScenarioInfo, List<ScenarioInfo>>> {
        val (specmaticScenarioInfo, openApiScenarioInfos) = parameters

        // exact + exact   -> values should be equal
        // exact + pattern -> error
        // pattern + exact -> pattern should match exact
        // pattern + pattern -> both generated concrete values should be of same type

        val matchingScenarioInfos = specmaticScenarioInfo.matchesGherkinWrapperPath(openApiScenarioInfos, this)

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

    override fun patternMatchesExact(
        wrapperURLPart: URLPathPattern,
        openapiURLPart: URLPathPattern,
        resolver: Resolver,
    ): Boolean {
        val valueFromWrapper = (wrapperURLPart.pattern as ExactValuePattern).pattern

        val valueToMatch: Value =
            if (valueFromWrapper is StringValue) {
                openapiURLPart.pattern.parse(valueFromWrapper.toStringLiteral(), resolver)
            } else {
                wrapperURLPart.pattern.pattern
            }

        return openapiURLPart.pattern.matches(valueToMatch, resolver) is Result.Success
    }

    override fun exactValuePatternsAreEqual(
        openapiURLPart: URLPathPattern,
        wrapperURLPart: URLPathPattern
    ) =
        (openapiURLPart.pattern as ExactValuePattern).pattern.toStringLiteral() == (wrapperURLPart.pattern as ExactValuePattern).pattern.toStringLiteral()

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

        return MatchSuccess(specmaticScenarioInfo to openApiScenarioInfos.map { openApiScenario ->
            val queryPattern = openApiScenario.httpRequestPattern.urlMatcher?.queryPattern ?: emptyMap()
            val zippedPathPatterns = (specmaticScenarioInfo.httpRequestPattern.urlMatcher?.pathPattern ?: emptyList()).zip(openApiScenario.httpRequestPattern.urlMatcher?.pathPattern ?: emptyList())

            val pathPatterns = zippedPathPatterns.map { (fromWrapper, fromOpenApi) ->
                if(fromWrapper.pattern is ExactValuePattern)
                    fromWrapper
                else
                    fromOpenApi.copy(key = fromWrapper.key)
            }

            val urlMatcher = URLMatcher(queryPattern, pathPatterns, openApiScenario.httpRequestPattern.urlMatcher?.path ?: "")

            val httpRequestPattern = openApiScenario.httpRequestPattern.copy(urlMatcher = urlMatcher)
            openApiScenario.copy(httpRequestPattern = httpRequestPattern)
        })
    }

    private fun openApitoScenarioInfos(): List<ScenarioInfo> {
        return openApiPaths().map { (openApiPath, pathItem) ->
            openApiOperations(pathItem).map { (httpMethod, operation) ->
                val specmaticPath = toSpecmaticPath2(openApiPath, operation)

                toHttpResponsePatterns(operation.responses).map { (response, responseMediaType, httpResponsePattern) ->
                    toHttpRequestPatterns(
                        specmaticPath, httpMethod, operation
                    ).map { httpRequestPattern: HttpRequestPattern ->
                        val scenarioName = scenarioName(operation, response, httpRequestPattern, null)

                        val ignoreFailure = operation.tags.orEmpty().map { it.trim() }.contains("WIP")
                        ScenarioInfo(
                            scenarioName = scenarioName,
                            patterns = patterns.toMap(),
                            httpRequestPattern = httpRequestPattern,
                            httpResponsePattern = httpResponsePattern,
                            ignoreFailure = ignoreFailure,
                        )
                    }
                }.flatten()
            }.flatten()
        }.flatten()
    }

    private fun scenarioName(
        operation: Operation, response: ApiResponse, httpRequestPattern: HttpRequestPattern, specmaticExampleRow: Row?
    ): String {
        val name = operation.summary?.let {
            """${operation.summary}. Response: ${response.description}"""
        } ?: "${httpRequestPattern.testDescription()}. Response: ${response.description}"

        return specmaticExampleRow?.let {
            "${name} Examples: ${specmaticExampleRow.stringForOpenAPIError()}"
        } ?: name
    }

    private fun toScenarioInfosWithExamples(): List<ScenarioInfo> {
        return openApiPaths().map { (openApiPath, pathItem) ->
            openApiOperations(pathItem).map { (httpMethod, operation) ->
                val specmaticPath = toSpecmaticPath2(openApiPath, operation)

                toHttpResponsePatterns(operation.responses).map { (response, responseMediaType, httpResponsePattern) ->
                    val responseExamples: Map<String, Example> = responseMediaType.examples.orEmpty()
                    val specmaticExampleRows: List<Row> = responseExamples.map { (exampleName, _) ->
                        val parameterExamples: Map<String, Any> = parameterExamples(operation, exampleName)

                        val requestBodyExample: Map<String, Any> = requestBodyExample(operation, exampleName)

                        val requestExamples = parameterExamples.plus(requestBodyExample).map { (key, value) ->
                            if (value?.toString().orEmpty().contains("externalValue")) "${key}_filename" to value
                            else key to value
                        }.toMap()

                        when {
                            requestExamples.isNotEmpty() -> Row(
                                requestExamples.keys.toList().map { keyName: String -> keyName },
                                requestExamples.values.toList().map { value: Any? -> value?.toString() ?: "" }
                                    .map { valueString: String ->
                                        if (valueString.contains("externalValue")) {
                                            ObjectMapper().readValue(valueString, Map::class.java).values.first()
                                                .toString()
                                        } else valueString
                                    },
                                name = exampleName)
                            else -> Row()
                        }
                    }

                    toHttpRequestPatterns(
                        specmaticPath, httpMethod, operation
                    ).map { httpRequestPattern: HttpRequestPattern ->
                        val scenarioName =
                            scenarioName(operation, response, httpRequestPattern, null)

                        val scenarioDetails = object : ScenarioDetailsForResult {
                            override val ignoreFailure: Boolean
                                get() = false
                            override val name: String
                                get() = scenarioName
                            override val method: String
                                get() = httpRequestPattern.method ?: ""
                            override val path: String
                                get() = httpRequestPattern.urlMatcher?.path ?: ""
                            override val status: Int
                                get() = httpResponsePattern.status
                            override val requestTestDescription: String
                                get() = httpRequestPattern.testDescription()
                        }

                        specmaticExampleRows.forEach { row ->
                            scenarioBreadCrumb(scenarioDetails) {
                                httpRequestPattern.newBasedOn(
                                    row,
                                    Resolver(newPatterns = this.patterns).copy(mismatchMessages = Scenario.ContractAndRowValueMismatch),
                                    httpResponsePattern.status
                                )
                            }
                        }

                        val ignoreFailure = operation.tags.orEmpty().map { it.trim() }.contains("WIP")

                        ScenarioInfo(
                            scenarioName = scenarioName,
                            patterns = patterns.toMap(),
                            httpRequestPattern = httpRequestPattern,
                            httpResponsePattern = httpResponsePattern,
                            ignoreFailure = ignoreFailure,
                            examples = if (specmaticExampleRows.isNotEmpty()) listOf(
                                Examples(
                                    specmaticExampleRows.first().columnNames,
                                    specmaticExampleRows
                                )
                            ) else emptyList()
                        )
                    }
                }.flatten()
            }.flatten()
        }.flatten()
    }

    private fun requestBodyExample(
        operation: Operation,
        exampleName: String
    ): Map<String, Any> {
        val requestExampleValue: Any? =
            operation.requestBody?.content?.values?.firstOrNull()?.examples?.get(exampleName)?.value

        val requestBodyExample: Map<String, Any> = if (requestExampleValue != null) {
            if (operation.requestBody?.content?.entries?.first()?.key == "application/x-www-form-urlencoded" || operation.requestBody?.content?.entries?.first()?.key == "multipart/form-data") {
                val jsonExample =
                    attempt("Could not parse example $exampleName for operation \"${operation.summary}\"") {
                        parsedJSON(requestExampleValue.toString()) as JSONObjectValue
                    }
                jsonExample.jsonObject.map { (key, value) ->
                    key to value.toString()
                }.toMap()
            } else {
                mapOf("(REQUEST-BODY)" to requestExampleValue)
            }
        } else {
            emptyMap()
        }
        return requestBodyExample
    }

    private fun parameterExamples(
        operation: Operation,
        exampleName: String
    ) = operation.parameters.orEmpty()
        .filter { parameter ->
            parameter.examples.orEmpty().any { it.key == exampleName }
        }
        .map {
            it.name to it.examples[exampleName]!!.value
        }.toMap()

    private fun openApiPaths() = openApi.paths.orEmpty()

    private fun toHttpResponsePatterns(responses: ApiResponses?): List<Triple<ApiResponse, MediaType, HttpResponsePattern>> {
        return responses.orEmpty().map { (status, response) ->
            val headersMap = openAPIHeadersToSpecmatic(response)
            openAPIResponseToSpecmatic(response, status, headersMap)
        }.flatten()
    }

    private fun openAPIHeadersToSpecmatic(response: ApiResponse) =
        response.headers.orEmpty().map { (headerName, header) ->
            toSpecmaticParamName(header.required != true, headerName) to toSpecmaticPattern(
                header.schema, emptyList()
            )
        }.toMap()

    private fun openAPIResponseToSpecmatic(
        response: ApiResponse,
        status: String,
        headersMap: Map<String, Pattern>
    ): List<Triple<ApiResponse, MediaType, HttpResponsePattern>> {
        if (response.content == null) {
            val responsePattern = HttpResponsePattern(
                headersPattern = HttpHeadersPattern(headersMap),
                status = status.toIntOrNull() ?: DEFAULT_RESPONSE_CODE
            )

            return listOf(Triple(response, MediaType(), responsePattern))
        }

        return response.content.map { (contentType, mediaType) ->
            val responsePattern = HttpResponsePattern(
                headersPattern = HttpHeadersPattern(headersMap),
                status = if (status == "default") 1000 else status.toInt(),
                body = when (contentType) {
                    "application/xml" -> toXMLPattern(mediaType)
                    else -> toSpecmaticPattern(mediaType)
                }
            )

            Triple(response, mediaType, responsePattern)
        }
    }

    private fun toHttpRequestPatterns(
        urlPathMatcher: URLMatcher, httpMethod: String, operation: Operation
    ): List<HttpRequestPattern> {

        val contractSecuritySchemes: Map<String, OpenAPISecurityScheme> =
            openApi.components?.securitySchemes?.mapValues { (_, scheme) ->
                toSecurityScheme(scheme)
            } ?: emptyMap()

        val parameters = operation.parameters

        val headersMap = parameters.orEmpty().filterIsInstance(HeaderParameter::class.java).map {
            toSpecmaticParamName(it.required != true, it.name) to toSpecmaticPattern(it.schema, emptyList())
        }.toMap().toMutableMap()

        val securityQueryParams = mutableSetOf<String>()

        val operationSecuritySchemes: List<OpenAPISecurityScheme> =
            operationSecuritySchemes(operation, contractSecuritySchemes)

        val urlMatcher = urlPathMatcher.withOptionalQueryParams(securityQueryParams)
        val headersPattern = HttpHeadersPattern(headersMap)
        val requestPattern = HttpRequestPattern(
            urlMatcher = urlMatcher,
            method = httpMethod,
            headersPattern = headersPattern,
            securitySchemes = operationSecuritySchemes
        )

        return when (operation.requestBody) {
            null -> listOf(
                requestPattern
            )
            else -> {
                val requestBody = if (operation.requestBody.`$ref` == null) {
                    operation.requestBody
                } else {
                    resolveReferenceToRequestBody(operation.requestBody.`$ref`).second
                }

                requestBody.content.map { (contentType, mediaType) ->
                    when (contentType.lowercase()) {
                        "multipart/form-data" -> {
                            val partSchemas = if (mediaType.schema.`$ref` == null) {
                                mediaType.schema
                            } else {
                                resolveReferenceToSchema(mediaType.schema.`$ref`).second
                            }

                            val parts: List<MultiPartFormDataPattern> =
                                partSchemas.properties.map { (partName, partSchema) ->
                                    val partContentType = mediaType.encoding?.get(partName)?.contentType
                                    val partNameWithPresence = if (partSchemas.required?.contains(partName) == true)
                                        partName
                                    else
                                        "$partName?"

                                    if (partSchema is BinarySchema) {
                                        MultiPartFilePattern(
                                            partNameWithPresence,
                                            toSpecmaticPattern(partSchema, emptyList()),
                                            partContentType
                                        )
                                    } else {
                                        MultiPartContentPattern(
                                            partNameWithPresence,
                                            toSpecmaticPattern(partSchema, emptyList()),
                                            partContentType
                                        )
                                    }
                                }

                            requestPattern.copy(multiPartFormDataPattern = parts)
                        }
                        "application/x-www-form-urlencoded" -> {
                            requestPattern.copy(formFieldsPattern = toFormFields(mediaType))
                        }
                        "application/xml" -> {
                            requestPattern.copy(body = toXMLPattern(mediaType))
                        }
                        else -> {
                            requestPattern.copy(body = toSpecmaticPattern(mediaType))
                        }
                    }
                }
            }
        }
    }

    private fun operationSecuritySchemes(
        operation: Operation,
        contractSecuritySchemes: Map<String, OpenAPISecurityScheme>
    ): List<OpenAPISecurityScheme> {
        val globalSecurityRequirements: List<String> =
            openApi.security?.map { it.keys.toList() }?.flatten() ?: emptyList()
        val operationSecurityRequirements: List<String> =
            operation.security?.map { it.keys.toList() }?.flatten() ?: emptyList()
        val operationSecurityRequirementsSuperSet: List<String> =
            globalSecurityRequirements.plus(operationSecurityRequirements).distinct()
        val operationSecuritySchemes: List<OpenAPISecurityScheme> =
            contractSecuritySchemes.filter { (name, scheme) -> name in operationSecurityRequirementsSuperSet }.values.toList()
        return operationSecuritySchemes
    }

    private fun toSecurityScheme(securityScheme: SecurityScheme): OpenAPISecurityScheme {
        if (securityScheme.scheme == BEARER_SECURITY_SCHEME)
            return BearerSecurityScheme()

        if (securityScheme.type == SecurityScheme.Type.APIKEY) {
            if (securityScheme.`in` == SecurityScheme.In.HEADER)
                return APIKeyInHeaderSecurityScheme(securityScheme.name)

            if (securityScheme.`in` == SecurityScheme.In.QUERY)
                return APIKeyInQueryParamSecurityScheme(securityScheme.name)
        }

        throw ContractException("Specmatic only supports bearer and api key authentication (header, query) security schemes at the moment")
    }

    private fun toFormFields(mediaType: MediaType) =
        mediaType.schema.properties.map { (formFieldName, formFieldValue) ->
            formFieldName to toSpecmaticPattern(
                formFieldValue, emptyList(), jsonInFormData = isJsonInString(mediaType, formFieldName)
            )
        }.toMap()

    private fun isJsonInString(
        mediaType: MediaType, formFieldName: String?
    ) = if (mediaType.encoding.isNullOrEmpty()) false
    else mediaType.encoding[formFieldName]?.contentType == "application/json"

    fun toSpecmaticPattern(mediaType: MediaType, jsonInFormData: Boolean = false): Pattern =
        toSpecmaticPattern(mediaType.schema, emptyList(), jsonInFormData = jsonInFormData)

    fun toSpecmaticPattern(
        schema: Schema<*>, typeStack: List<String>, patternName: String = "", jsonInFormData: Boolean = false
    ): Pattern {
        val preExistingResult = patterns.get("($patternName)")
        val pattern = if (preExistingResult != null && !patternName.isNullOrBlank())
            preExistingResult
        else if (typeStack.filter { it == patternName }.size > 1) {
            DeferredPattern("($patternName)")
        }
        else when (schema) {
            is StringSchema -> when (schema.enum) {
                null -> StringPattern(minLength = schema.minLength, maxLength = schema.maxLength)
                else -> toEnum(schema, patternName) { enumValue -> StringValue(enumValue.toString()) }
            }
            is IntegerSchema -> when (schema.enum) {
                null -> NumberPattern()
                else -> toEnum(schema, patternName) { enumValue -> NumberValue(enumValue.toString().toInt()) }
            }
            is BinarySchema -> BinaryPattern()
            is NumberSchema -> NumberPattern()
            is UUIDSchema -> StringPattern()
            is DateTimeSchema -> DateTimePattern
            is DateSchema -> StringPattern()
            is BooleanSchema -> BooleanPattern
            is ObjectSchema -> {
                if (schema.additionalProperties != null) {
                    toDictionaryPattern(schema, typeStack, patternName)
                } else if (schema.xml?.name != null) {
                    toXMLPattern(schema, typeStack = typeStack)
                } else {
                    toJsonObjectPattern(schema, patternName, typeStack)
                }
            }
            is ArraySchema -> {
                if (schema.xml?.name != null) {
                    toXMLPattern(schema, typeStack = typeStack)
                } else {

                    JSONArrayPattern(listOf(toSpecmaticPattern(schema.items, typeStack)))
                }
            }
            is ComposedSchema -> {
                if (schema.allOf != null) {
                    val schemaProperties = schema.allOf.map { constituentSchema ->
                        val schemaToProcess = if (constituentSchema.`$ref` != null) {
                            val (_, referredSchema) = resolveReferenceToSchema(constituentSchema.`$ref`)
                            referredSchema
                        } else constituentSchema

                        val requiredFields = schemaToProcess.required.orEmpty()
                        toSchemaProperties(schemaToProcess, requiredFields, patternName, typeStack)
                    }.fold(emptyMap<String, Pattern>()) { acc, entry -> acc.plus(entry) }
                    val jsonObjectPattern = toJSONObjectPattern(schemaProperties, "(${patternName})")
                    jsonObjectPattern
                } else if (schema.oneOf != null) {
                    val candidatePatterns = schema.oneOf.filterNot { nullableEmptyObject(it) } .map { componentSchema ->
                        val (componentName, schemaToProcess) =
                            if (componentSchema.`$ref` != null) resolveReferenceToSchema(componentSchema.`$ref`)
                            else patternName to componentSchema
                        toSpecmaticPattern(schemaToProcess, typeStack.plus(componentName), componentName)
                    }

                    val nullable = if(schema.oneOf.any { nullableEmptyObject(it) }) listOf(NullPattern) else emptyList()

                    AnyPattern(candidatePatterns.plus(nullable))
                } else if (schema.anyOf != null) {
                    throw UnsupportedOperationException("Specmatic does not support anyOf")
                } else {
                    throw UnsupportedOperationException("Unsupported composed schema: $schema")
                }
            }
            else -> {
                if (schema.nullable == true && schema.additionalProperties == null && schema.`$ref` == null) {
                    NullPattern
                } else if (schema.additionalProperties != null) {
                    toDictionaryPattern(schema, typeStack, patternName)
                } else {
                    when (schema.`$ref`) {
                        null -> toJsonObjectPattern(schema, patternName, typeStack)
                        else -> {
                            val component: String = schema.`$ref`

                            val (componentName, referredSchema) = resolveReferenceToSchema(component)
                            val cyclicReference = typeStack.contains(componentName)
                            if (!cyclicReference) {
                                val componentPattern = toSpecmaticPattern(referredSchema,
                                    typeStack.plus(componentName), componentName)
                                cacheComponentPattern(componentName, componentPattern)
                            }
                            DeferredPattern("(${componentName})")
                        }
                    }
                }
            }
        }.also {
            when {
                it.instanceOf(JSONObjectPattern::class) && jsonInFormData -> {
                    PatternInStringPattern(
                        patterns.getOrDefault("($patternName)", StringPattern()), "($patternName)"
                    )
                }
                else -> it
            }
        }

        return when (schema.nullable != true) {
            true -> pattern
            else -> when (pattern) {
                is AnyPattern, NullPattern -> pattern
                else -> AnyPattern(listOf(NullPattern, pattern))
            }
        }
    }

    private fun <T : Pattern> cacheComponentPattern(componentName: String, pattern: T): T {
        if (!componentName.isNullOrBlank() && pattern !is DeferredPattern) {
            val typeName = "(${componentName})"
            val prev = patterns.get(typeName)
            if (pattern != prev) {
                if (prev != null) {
                    logger.debug("Replacing cached component pattern. name=$componentName, prev=$prev, new=$pattern")
                }
                patterns[typeName] = pattern
            }
        }
        return pattern
    }

    private fun nullableEmptyObject(schema: Schema<*>): Boolean {
        return schema is ObjectSchema && schema.nullable == true
    }

    private fun toXMLPattern(mediaType: MediaType): Pattern {
        return toXMLPattern(mediaType.schema, typeStack = emptyList())
    }

    private fun toXMLPattern(
        schema: Schema<Any>, nodeNameFromProperty: String? = null, typeStack: List<String>
    ): XMLPattern {
        val name = schema.xml?.name ?: nodeNameFromProperty

        return when (schema) {
            is ObjectSchema -> {
                val nodeProperties = schema.properties.filter { entry ->
                    entry.value.xml?.attribute != true
                }

                val nodes = nodeProperties.map { (propertyName: String, propertySchema) ->
                    val type = when (propertySchema.type) {
                        in primitiveOpenAPITypes -> {
                            val innerPattern = DeferredPattern(primitiveOpenAPITypes.getValue(propertySchema.type))
                            XMLPattern(XMLTypeData(propertyName, propertyName, emptyMap(), listOf(innerPattern)))
                        }
                        else -> {
                            toXMLPattern(propertySchema, propertyName, typeStack)
                        }
                    }

                    val optionalAttribute = if (propertyName !in (schema.required ?: emptyList<String>())) mapOf(
                        OCCURS_ATTRIBUTE_NAME to ExactValuePattern(StringValue(OPTIONAL_ATTRIBUTE_VALUE))
                    )
                    else emptyMap()

                    type.copy(pattern = type.pattern.copy(attributes = optionalAttribute.plus(type.pattern.attributes)))
                }

                val attributeProperties = schema.properties.filter { entry ->
                    entry.value.xml?.attribute == true
                }

                val attributes: Map<String, Pattern> = attributeProperties.map { (name, schema) ->
                    name to toSpecmaticPattern(schema, emptyList())
                }.toMap()

                name ?: throw ContractException("Could not determine name for an xml node")

                val namespaceAttributes: Map<String, ExactValuePattern> =
                    if (schema.xml?.namespace != null && schema.xml?.prefix != null) {
                        val attributeName = "xmlns:${schema.xml?.prefix}"
                        val attributeValue = ExactValuePattern(StringValue(schema.xml.namespace))
                        mapOf(attributeName to attributeValue)
                    } else {
                        emptyMap()
                    }

                val xmlTypeData = XMLTypeData(name, realName(schema, name), namespaceAttributes.plus(attributes), nodes)

                XMLPattern(xmlTypeData)
            }
            is ArraySchema -> {
                val repeatingSchema = schema.items as Schema<Any>

                val repeatingType = when (repeatingSchema.type) {
                    in primitiveOpenAPITypes -> {
                        val innerPattern = DeferredPattern(primitiveOpenAPITypes.getValue(repeatingSchema.type))

                        val innerName = repeatingSchema.xml?.name
                            ?: if (schema.xml?.name != null && schema.xml?.wrapped == true) schema.xml.name else nodeNameFromProperty

                        XMLPattern(
                            XMLTypeData(
                                innerName ?: throw ContractException("Could not determine name for an xml node"),
                                innerName,
                                emptyMap(),
                                listOf(innerPattern)
                            )
                        )
                    }
                    else -> {
                        toXMLPattern(repeatingSchema, name, typeStack)
                    }
                }.let { repeatingType ->
                    repeatingType.copy(
                        pattern = repeatingType.pattern.copy(
                            attributes = repeatingType.pattern.attributes.plus(
                                OCCURS_ATTRIBUTE_NAME to ExactValuePattern(StringValue(MULTIPLE_ATTRIBUTE_VALUE))
                            )
                        )
                    )
                }

                if (schema.xml?.wrapped == true) {
                    val wrappedName = schema.xml?.name ?: nodeNameFromProperty
                    val wrapperTypeData = XMLTypeData(
                        wrappedName ?: throw ContractException("Could not determine name for an xml node"),
                        wrappedName,
                        emptyMap(),
                        listOf(repeatingType)
                    )
                    XMLPattern(wrapperTypeData)
                } else repeatingType
            }
            else -> {
                if (schema.`$ref` != null) {
                    val component = schema.`$ref`
                    val (componentName, componentSchema) = resolveReferenceToSchema(component)

                    val typeName = "($componentName)"

                    val nodeName = componentSchema.xml?.name ?: name ?: componentName

                    if (typeName !in typeStack) {
                        val componentPattern = toXMLPattern(componentSchema, componentName, typeStack.plus(typeName))
                        cacheComponentPattern(componentName, componentPattern)
                    }

                    val xmlRefType = XMLTypeData(
                        nodeName, nodeName, mapOf(
                            TYPE_ATTRIBUTE_NAME to ExactValuePattern(
                                StringValue(
                                    componentName
                                )
                            )
                        ), emptyList()
                    )

                    XMLPattern(xmlRefType)
                } else throw ContractException("Node not recognized as XML type: ${schema.type}")
            }
        }
    }

    private fun realName(schema: ObjectSchema, name: String): String = if (schema.xml?.prefix != null) {
        "${schema.xml?.prefix}:${name}"
    } else {
        name
    }

    private val primitiveOpenAPITypes =
        mapOf("string" to "(string)", "number" to "(number)", "integer" to "(number)", "boolean" to "(boolean)")

    private fun toDictionaryPattern(
        schema: Schema<*>, typeStack: List<String>, patternName: String
    ): DictionaryPattern {
        val valueSchema = schema.additionalProperties as Schema<Any>
        val valueSchemaTypeName = valueSchema.`$ref` ?: valueSchema.types?.first() ?: "";
        return DictionaryPattern(
            StringPattern(), toSpecmaticPattern(valueSchema, typeStack, valueSchemaTypeName, false)
        )
    }

    private fun toJsonObjectPattern(
        schema: Schema<*>, patternName: String, typeStack: List<String>
    ): JSONObjectPattern {
        val requiredFields = schema.required.orEmpty()
        val schemaProperties = toSchemaProperties(schema, requiredFields, patternName, typeStack)
        val jsonObjectPattern = toJSONObjectPattern(schemaProperties, "(${patternName})")
        return cacheComponentPattern(patternName, jsonObjectPattern)
    }

    private fun toSchemaProperties(
        schema: Schema<*>, requiredFields: List<String>, patternName: String, typeStack: List<String>
    ) = schema.properties.orEmpty().map { (propertyName, propertyType) ->
        if (schema.discriminator?.propertyName == propertyName)
            propertyName to ExactValuePattern(StringValue(patternName))
        else {
            val optional = !requiredFields.contains(propertyName)
            toSpecmaticParamName(optional, propertyName) to toSpecmaticPattern(propertyType, typeStack)
        }
    }.toMap()

    private fun toEnum(schema: Schema<*>, patternName: String, toSpecmaticValue: (Any) -> Value) =
        AnyPattern(schema.enum.map<Any, Pattern> { enumValue ->
            when (enumValue) {
                null -> NullPattern
                else -> ExactValuePattern(toSpecmaticValue(enumValue))
            }
        }.toList(), typeAlias = patternName).also {
            cacheComponentPattern(patternName, it)
        }

    private fun toSpecmaticParamName(optional: Boolean, name: String) = when (optional) {
        true -> "${name}?"
        false -> name
    }

    private fun resolveReferenceToSchema(component: String): Pair<String, Schema<Any>> {
        val componentName = extractComponentName(component)
        val schema = openApi.components.schemas[componentName] ?: ObjectSchema().also { it.properties = emptyMap() }

        return componentName to schema as Schema<Any>
    }

    private fun resolveReferenceToRequestBody(component: String): Pair<String, RequestBody> {
        val componentName = extractComponentName(component)
        val requestBody = openApi.components.requestBodies[componentName] ?: RequestBody()

        return componentName to requestBody
    }

    private fun extractComponentName(component: String): String {
        if (!component.startsWith("#")) throw UnsupportedOperationException("Specmatic only supports local component references.")
        val componentName = componentNameFromReference(component)
        return componentName
    }

    private fun componentNameFromReference(component: String) = component.substringAfterLast("/")

    private fun toSpecmaticPath2(openApiPath: String, operation: Operation): URLMatcher {
        val parameters = operation.parameters ?: return toURLMatcherWithOptionalQueryParams(openApiPath)

        val pathStringParts: List<String> = openApiPath.removePrefix("/").removeSuffix("/").let {
            if (it.isBlank())
                emptyList()
            else it.split("/")
        }
        val pathParamMap: Map<String, PathParameter> =
            parameters.filterIsInstance(PathParameter::class.java).associateBy {
                it.name
            }

        val pathPattern: List<URLPathPattern> = pathStringParts.map {
            if(it.startsWith("{") && it.endsWith("}")) {
                val paramName = it.removeSurrounding("{", "}")

                pathParamMap[paramName]?.let {
                    URLPathPattern(toSpecmaticPattern(it.schema, emptyList()), paramName)
                } ?: throw ContractException("The path parameter in $openApiPath is not defined in the specification")
            } else {
                URLPathPattern(ExactValuePattern(StringValue(it)))
            }
        }

        val queryPattern: Map<String, Pattern> = parameters.filterIsInstance(QueryParameter::class.java).associate {
            val specmaticPattern: Pattern = if (it.schema.type == "array") {
                CsvPattern(toSpecmaticPattern(schema = it.schema.items, typeStack = emptyList()))
            } else {
                toSpecmaticPattern(schema = it.schema, typeStack = emptyList(), patternName = it.name)
            }

            "${it.name}?" to specmaticPattern
        }

        val specmaticPath = toSpecmaticFormattedPathString(parameters, openApiPath)

        return URLMatcher(queryPattern, pathPattern, specmaticPath)
    }

    private fun toSpecmaticFormattedPathString(
        parameters: List<Parameter>,
        openApiPath: String
    ): String {
        return parameters.filterIsInstance(PathParameter::class.java).foldRight(openApiPath) { it, specmaticPath ->
            val pattern = if (it.schema.enum != null) StringPattern("") else toSpecmaticPattern(it.schema, emptyList())
            specmaticPath.replace(
                "{${it.name}}", "(${it.name}:${pattern.typeName})"
            )
        }
    }

    private fun openApiOperations(pathItem: PathItem): Map<String, Operation> = mapOf<String, Operation?>(
        "GET" to pathItem.get,
        "POST" to pathItem.post,
        "DELETE" to pathItem.delete,
        "PUT" to pathItem.put,
        "PATCH" to pathItem.patch
    ).filter { (_, value) -> value != null }.map { (key, value) -> key to value!! }.toMap()
}

