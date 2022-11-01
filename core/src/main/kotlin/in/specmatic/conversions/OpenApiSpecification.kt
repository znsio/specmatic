package `in`.specmatic.conversions

import `in`.specmatic.core.*
import `in`.specmatic.core.Result.Failure
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
import io.swagger.v3.oas.models.parameters.HeaderParameter
import io.swagger.v3.oas.models.parameters.PathParameter
import io.swagger.v3.oas.models.parameters.QueryParameter
import io.swagger.v3.oas.models.parameters.RequestBody
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.parser.OpenAPIV3Parser
import io.swagger.v3.parser.core.models.ParseOptions
import io.swagger.v3.parser.core.models.SwaggerParseResult
import java.io.File

private const val BEARER_SECURITY_SCHEME = "bearer"

class OpenApiSpecification(private val openApiFile: String, val openApi: OpenAPI) : IncludedSpecification {
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

        fun fromYAML(yamlContent: String, filePath: String): OpenApiSpecification {
            val parseResult: SwaggerParseResult =
                OpenAPIV3Parser().readContents(yamlContent, null, resolveExternalReferences(), filePath)
            val openApi: OpenAPI? = parseResult.openAPI

            if (openApi == null) {
                logger.debug("Failed to parse OpenAPI from file $filePath\n\n$yamlContent")

                parseResult.messages.filterNotNull().let {
                    if (it.isNotEmpty()) {
                        val parserMessages = parseResult.messages.joinToString(System.lineSeparator())
                        logger.log("Error parsing file $filePath")
                        logger.log(parserMessages.prependIndent("  "))
                    }
                }

                throw ContractException("Could not parse contract $filePath, please validate the syntax using https://editor.swagger.io")
            }

            return OpenApiSpecification(filePath, openApi)
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

        val matchingScenarioInfos = openApiScenarioInfos.filter {
            it.httpRequestPattern.urlMatcher!!.matches(
                specmaticScenarioInfo.httpRequestPattern.generate(
                    Resolver()
                ), Resolver()
            ).isSuccess()
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
            val queryPattern = it.httpRequestPattern.urlMatcher?.queryPattern ?: emptyMap()
            val urlMatcher = specmaticScenarioInfo.httpRequestPattern.urlMatcher?.copy(queryPattern = queryPattern)
            val httpRequestPattern = it.httpRequestPattern.copy(urlMatcher = urlMatcher)
            it.copy(httpRequestPattern = httpRequestPattern)
        })
    }

    private fun openApitoScenarioInfos(): List<ScenarioInfo> {
        return openApiPaths().map { (openApiPath, pathItem) ->
            openApiOperations(pathItem).map { (httpMethod, operation) ->
                val specmaticPath = toSpecmaticPath(openApiPath, operation)

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
                val specmaticPath = toSpecmaticPath(openApiPath, operation)

                toHttpResponsePatterns(operation.responses).map { (response, responseMediaType, httpResponsePattern) ->
                    val responseExamples: Map<String, Example> = responseMediaType.examples.orEmpty()
                    val specmaticExampleRows: List<Row> = responseExamples.map { (exampleName, _) ->
                        val parameterExamples: Map<String, Any> = parameterExamples(operation, exampleName)

                        val requestBodyExample: Map<String, Any> = requestBodyExample(operation, exampleName)

                        val requestExamples = parameterExamples.plus(requestBodyExample).map { (key, value) ->
                            if (value.toString().contains("externalValue")) "${key}_filename" to value
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
                                    })
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
                                    Resolver(newPatterns = this.patterns).copy(mismatchMessages = Scenario.ContractAndRowValueMismatch)
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
        path: String, httpMethod: String, operation: Operation
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

        val urlMatcher = toURLMatcherWithOptionalQueryParams(path, securityQueryParams)
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

    private fun unsupportedApiKeyAuth(securityScheme: Pair<String, SecurityScheme>): Boolean {
        return (securityScheme.second.type != SecurityScheme.Type.APIKEY
                || securityScheme.second.`in` !in listOf(SecurityScheme.In.HEADER, SecurityScheme.In.QUERY))
    }

    private fun notBearerAuth(securityScheme: Pair<String, SecurityScheme>): Boolean {
        return securityScheme.second.scheme != BEARER_SECURITY_SCHEME
    }

    private fun doSecurityRequirementsMatch(
        securityRequirements: MutableList<SecurityRequirement>?, securitySchemeName: String
    ) = securityRequirements != null && securityRequirements.toList().any { it.keys.contains(securitySchemeName) }

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
        val pattern = when (schema) {
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
                    patterns["(${patternName})"] = jsonObjectPattern
                    jsonObjectPattern
                } else if (nullableOneOf(schema)) {
                    AnyPattern(listOf(NullPattern, toSpecmaticPattern(nonNullSchema(schema), typeStack, patternName)))
                } else {
                    throw UnsupportedOperationException("Specmatic does not support anyOf. Only allOf is supported, or oneOf for specifying nullable refs.")
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
                            val cyclicReference =
                                patternName.isNotEmpty() && patternName == componentName && typeStack.contains(
                                    patternName
                                ) && referredSchema.instanceOf(ObjectSchema::class)
                            when {
                                cyclicReference -> DeferredPattern("(${patternName})")
                                else -> {
                                    val componentType = resolveReference(component, typeStack)
                                    val typeName = "(${componentNameFromReference(component)})"
                                    patterns[typeName] = componentType
                                    DeferredPattern(typeName)
                                }
                            }
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

                    if (typeName !in typeStack) this.patterns[typeName] =
                        toXMLPattern(componentSchema, componentName, typeStack.plus(typeName))

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
        val valueSchemaTypeName = if (valueSchema.`$ref` == null) valueSchema.types.first() else valueSchema.`$ref`
        return DictionaryPattern(
            StringPattern(), toSpecmaticPattern(valueSchema, typeStack, valueSchemaTypeName, false)
        )
    }

    private fun nonNullSchema(schema: ComposedSchema): Schema<Any> {
        return schema.oneOf.first { it.nullable != true }
    }

    private fun nullableOneOf(schema: ComposedSchema): Boolean {
        return schema.oneOf.find { it.nullable != true } != null
    }

    private fun toJsonObjectPattern(
        schema: Schema<*>, patternName: String, typeStack: List<String>
    ): JSONObjectPattern {
        val requiredFields = schema.required.orEmpty()
        val schemaProperties = toSchemaProperties(schema, requiredFields, patternName, typeStack)
        val jsonObjectPattern = toJSONObjectPattern(schemaProperties, "(${patternName})")
        patterns["(${patternName})"] = jsonObjectPattern
        return jsonObjectPattern
    }

    private fun toSchemaProperties(
        schema: Schema<*>, requiredFields: List<String>, patternName: String, typeStack: List<String>
    ) = schema.properties.orEmpty().map { (propertyName, propertyType) ->
        val optional = !requiredFields.contains(propertyName)
        if (patternName.isNotEmpty()) {
            if (typeStack.contains(patternName) && (propertyType.`$ref`.orEmpty()
                    .endsWith(patternName) || (propertyType is ArraySchema && propertyType.items?.`$ref`?.endsWith(
                    patternName
                ) == true))
            ) toSpecmaticParamName(
                optional, propertyName
            ) to DeferredPattern("(${patternName})")
            else {
                val specmaticPattern = toSpecmaticPattern(
                    propertyType, typeStack.plus(patternName), patternName
                )
                toSpecmaticParamName(optional, propertyName) to specmaticPattern
            }
        } else {
            toSpecmaticParamName(optional, propertyName) to toSpecmaticPattern(
                propertyType, typeStack
            )
        }
    }.toMap()

    private fun toEnum(schema: Schema<*>, patternName: String, toSpecmaticValue: (Any) -> Value) =
        AnyPattern(schema.enum.map<Any, Pattern> { enumValue ->
            when (enumValue) {
                null -> NullPattern
                else -> ExactValuePattern(toSpecmaticValue(enumValue))
            }
        }.toList(), typeAlias = patternName).also { if (patternName.isNotEmpty()) patterns["(${patternName})"] = it }

    private fun toSpecmaticParamName(optional: Boolean, name: String) = when (optional) {
        true -> "${name}?"
        false -> name
    }

    private fun resolveReference(component: String, typeStack: List<String>): Pattern {
        val (componentName, referredSchema) = resolveReferenceToSchema(component)
        return toSpecmaticPattern(referredSchema, typeStack, patternName = componentName)
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

    private fun toSpecmaticPath(openApiPath: String, operation: Operation): String {
        val parameters = operation.parameters ?: return openApiPath

        var specmaticPath = openApiPath
        parameters.filterIsInstance(PathParameter::class.java).map {
            specmaticPath = specmaticPath.replace(
                "{${it.name}}", "(${it.name}:${toSpecmaticPattern(it.schema, emptyList()).typeName})"
            )
        }

        val queryParameters = parameters.filterIsInstance(QueryParameter::class.java).joinToString("&") {
            val specmaticPattern =
                toSpecmaticPattern(schema = it.schema, typeStack = emptyList(), patternName = it.name)
            val patternName = when {
                it.schema.enum != null -> specmaticPattern.run { "($typeAlias)" }
                else -> specmaticPattern
            }
            "${it.name}=$patternName"
        }

        if (queryParameters.isNotEmpty()) specmaticPath = "${specmaticPath}?${queryParameters}"

        return specmaticPath
    }

    private fun openApiOperations(pathItem: PathItem): Map<String, Operation> = mapOf<String, Operation?>(
        "GET" to pathItem.get, "POST" to pathItem.post, "DELETE" to pathItem.delete, "PUT" to pathItem.put
    ).filter { (_, value) -> value != null }.map { (key, value) -> key to value!! }.toMap()
}
