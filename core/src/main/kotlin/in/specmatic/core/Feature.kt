package `in`.specmatic.core

import `in`.specmatic.conversions.*
import `in`.specmatic.conversions.Environment
import `in`.specmatic.core.log.logger
import `in`.specmatic.core.pattern.*
import `in`.specmatic.core.pattern.Examples.Companion.examplesFrom
import `in`.specmatic.core.utilities.capitalizeFirstChar
import `in`.specmatic.core.utilities.jsonStringToValueMap
import `in`.specmatic.core.utilities.readEnvVarOrProperty
import `in`.specmatic.core.value.*
import `in`.specmatic.mock.NoMatchingScenario
import `in`.specmatic.mock.ScenarioStub
import `in`.specmatic.stub.HttpStubData
import `in`.specmatic.test.ContractTest
import `in`.specmatic.test.ScenarioTest
import `in`.specmatic.test.TestExecutor
import io.cucumber.gherkin.GherkinDocumentBuilder
import io.cucumber.gherkin.Parser
import io.cucumber.messages.IdGenerator
import io.cucumber.messages.IdGenerator.Incrementing
import io.cucumber.messages.types.*
import io.cucumber.messages.types.Examples
import io.swagger.v3.oas.models.*
import io.swagger.v3.oas.models.headers.Header
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.*
import io.swagger.v3.oas.models.parameters.*
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import java.io.File
import java.net.URI

fun parseContractFileToFeature(contractPath: String, hook: Hook = PassThroughHook(), sourceProvider:String? = null, sourceRepository:String? = null,  sourceRepositoryBranch:String? = null, specificationPath:String? = null, securityConfiguration: SecurityConfiguration? = null, environment: Environment = DefaultEnvironment()): Feature {
    return parseContractFileToFeature(File(contractPath), hook, sourceProvider, sourceRepository, sourceRepositoryBranch, specificationPath, securityConfiguration, environment)
}

fun checkExists(file: File) = file.also {
    if (!file.exists())
        throw ContractException("File ${file.path} does not exist (absolute path ${file.canonicalPath})")
}

fun parseContractFileToFeature(file: File, hook: Hook = PassThroughHook(), sourceProvider:String? = null, sourceRepository:String? = null,  sourceRepositoryBranch:String? = null, specificationPath:String? = null, securityConfiguration: SecurityConfiguration? = null, environment: Environment = DefaultEnvironment()): Feature {
    logger.debug("Parsing contract file ${file.path}, absolute path ${file.absolutePath}")

    return when (file.extension) {
        in OPENAPI_FILE_EXTENSIONS -> OpenApiSpecification.fromYAML(hook.readContract(file.path), file.path, sourceProvider =sourceProvider, sourceRepository = sourceRepository, sourceRepositoryBranch = sourceRepositoryBranch, specificationPath = specificationPath, securityConfiguration = securityConfiguration, environment = environment).toFeature()
        WSDL -> wsdlContentToFeature(checkExists(file).readText(), file.canonicalPath)
        in CONTRACT_EXTENSIONS -> parseGherkinStringToFeature(checkExists(file).readText().trim(), file.canonicalPath)
        else -> throw unsupportedFileExtensionContractException(file.path, file.extension)
    }
}

fun unsupportedFileExtensionContractException(
    path: String,
    extension: String
) =
    ContractException(
        "Current file $path has an unsupported extension $extension. Supported extensions are ${
            CONTRACT_EXTENSIONS.joinToString(
                ", "
            )
        }."
    )

fun parseGherkinStringToFeature(gherkinData: String, sourceFilePath: String = ""): Feature {
    val gherkinDocument = parseGherkinString(gherkinData, sourceFilePath)
    val (name, scenarios) = lex(gherkinDocument, sourceFilePath)
    return Feature(scenarios = scenarios, name = name, path = sourceFilePath)
}

data class Feature(
    val scenarios: List<Scenario> = emptyList(),
    private var serverState: Map<String, Value> = emptyMap(),
    val name: String,
    val testVariables: Map<String, String> = emptyMap(),
    val testBaseURLs: Map<String, String> = emptyMap(),
    val path: String = "",
    val sourceProvider:String? = null,
    val sourceRepository:String? = null,
    val sourceRepositoryBranch:String? = null,
    val specification:String? = null,
    val serviceType:String? = null,
    val stubsFromExamples: Map<String, List<Pair<HttpRequest, HttpResponse>>> = emptyMap(),
    val resolverStrategies: ResolverStrategies = strategiesFromFlags()
) {
    fun enableGenerativeTesting(onlyPositive: Boolean = false): Feature {
        return this.copy(resolverStrategies = this.resolverStrategies.copy(generation = GenerativeTestsEnabled(onlyPositive)))
    }

    fun enableSchemaExampleDefault(): Feature {
        return this.copy(resolverStrategies = this.resolverStrategies.copy(defaultExampleResolver = UseDefaultExample))
    }

    fun lookupResponse(httpRequest: HttpRequest): HttpResponse {
        try {
            val resultList = lookupScenario(httpRequest, scenarios)
            return matchingScenario(resultList)?.generateHttpResponse(serverState)
                ?: Results(resultList.map { it.second }.toMutableList()).withoutFluff()
                    .generateErrorHttpResponse(httpRequest)
        } finally {
            serverState = emptyMap()
        }
    }

    fun stubResponse(
        httpRequest: HttpRequest,
        mismatchMessages: MismatchMessages = DefaultMismatchMessages
    ): Pair<ResponseBuilder?, Results> {
        try {
            val scenarioSequence = scenarios.asSequence()

            val localCopyOfServerState = serverState
            val resultList = scenarioSequence.zip(scenarioSequence.map {
                it.matchesStub(httpRequest, localCopyOfServerState, mismatchMessages)
            })

            return matchingScenario(resultList)?.let { Pair(ResponseBuilder(it, serverState), Results()) }
                ?: Pair(null, Results(resultList.map { it.second }.toMutableList()).withoutFluff())
        } finally {
            serverState = emptyMap()
        }
    }

    fun compatibilityLookup(httpRequest: HttpRequest, mismatchMessages: MismatchMessages = NewAndOldContractRequestMismatches): List<Pair<Scenario, Result>> {
        try {
            val resultList = lookupAllScenarios(httpRequest, scenarios, mismatchMessages, IgnoreUnexpectedKeys)

            val successes = lookupAllSuccessfulScenarios(resultList)
            if (successes.isNotEmpty())
                return successes

            val deepMatchingErrors = allDeeplyMatchingScenarios(resultList)

            return when {
                deepMatchingErrors.isNotEmpty() -> deepMatchingErrors
                scenarios.isEmpty() -> throw EmptyContract()
                else -> emptyList()
            }
        } finally {
            serverState = emptyMap()
        }
    }

    private fun lookupAllSuccessfulScenarios(resultList: List<Pair<Scenario, Result>>): List<Pair<Scenario, Result>> {
        return resultList.filter { (_, result) ->
            result is Result.Success
        }
    }

    private fun allDeeplyMatchingScenarios(resultList: List<Pair<Scenario, Result>>): List<Pair<Scenario, Result>> {
        return resultList.filter {
            when (val result = it.second) {
                is Result.Success -> true
                is Result.Failure -> !result.isFluffy()
            }
        }
    }

    private fun matchingScenario(resultList: Sequence<Pair<Scenario, Result>>): Scenario? {
        return resultList.find {
            it.second is Result.Success
        }?.first
    }

    private fun lookupScenario(
        httpRequest: HttpRequest,
        scenarios: List<Scenario>
    ): Sequence<Pair<Scenario, Result>> {
        val scenarioSequence = scenarios.asSequence()

        val localCopyOfServerState = serverState
        return scenarioSequence.zip(scenarioSequence.map {
            it.matches(httpRequest, localCopyOfServerState, DefaultMismatchMessages)
        })
    }

    private fun lookupAllScenarios(
        httpRequest: HttpRequest,
        scenarios: List<Scenario>,
        mismatchMessages: MismatchMessages = DefaultMismatchMessages,
        unexpectedKeyCheck: UnexpectedKeyCheck? = null
    ): List<Pair<Scenario, Result>> {
        val localCopyOfServerState = serverState
        return scenarios.zip(scenarios.map {
            it.matches(httpRequest, localCopyOfServerState, mismatchMessages, unexpectedKeyCheck)
        })
    }

    fun executeTests(testExecutorFn: TestExecutor, suggestions: List<Scenario> = emptyList()): Results {
        val testScenarios = generateContractTestScenarios(suggestions)

        return testScenarios.fold(Results()) { results, scenario ->
            Results(results = results.results.plus(executeTest(scenario, testExecutorFn, resolverStrategies)).toMutableList())
        }
    }

    fun executeTests(
        testExecutorFn: TestExecutor,
        suggestions: List<Scenario> = emptyList(),
        scenarioNames: List<String>
    ): Results =
        generateContractTestScenarios(suggestions)
            .filter { scenarioNames.contains(it.name) }
            .fold(Results()) { results, scenario ->
                Results(results = results.results.plus(executeTest(scenario, testExecutorFn, resolverStrategies)).toMutableList())
            }

    fun setServerState(serverState: Map<String, Value>) {
        this.serverState = this.serverState.plus(serverState)
    }

    fun matches(request: HttpRequest, response: HttpResponse): Boolean {
        return scenarios.firstOrNull {
            it.matches(
                request,
                serverState
            ) is Result.Success && it.matches(response) is Result.Success
        } != null
    }

    fun matchingStub(
        request: HttpRequest,
        response: HttpResponse,
        mismatchMessages: MismatchMessages = DefaultMismatchMessages
    ): HttpStubData {
        try {
            val results = stubMatchResult(request, response, mismatchMessages)

            return results.find {
                it.first != null
            }?.let { it.first as HttpStubData }
                ?: throw NoMatchingScenario(
                    failureResults(results).withoutFluff(),
                    msg = null,
                    cachedMessage = failureResults(results).withoutFluff().report(request)
                )
        } finally {
            serverState = emptyMap()
        }
    }

    fun stubMatchResult(
        request: HttpRequest,
        response: HttpResponse,
        mismatchMessages: MismatchMessages
    ): List<Pair<HttpStubData?, Result>> {
        val results = scenarios.map { scenario ->
            try {
                when (val matchResult = scenario.matchesMock(request, response, mismatchMessages)) {
                    is Result.Success -> Pair(
                        scenario.resolverAndResponseFrom(response).let { (resolver, resolvedResponse) ->
                            val newRequestType = scenario.httpRequestPattern.generate(request, resolver)
                            val requestTypeWithAncestors =
                                newRequestType.copy(
                                    headersPattern = newRequestType.headersPattern.copy(
                                        ancestorHeaders = scenario.httpRequestPattern.headersPattern.pattern
                                    )
                                )
                            HttpStubData(
                                response = resolvedResponse.copy(externalisedResponseCommand = response.externalisedResponseCommand),
                                resolver = resolver,
                                requestType = requestTypeWithAncestors,
                                responsePattern = scenario.httpResponsePattern,
                                contractPath = this.path,
                                feature = this,
                                scenario = scenario
                            )
                        }, Result.Success()
                    )

                    is Result.Failure -> {
                        Pair(null, matchResult.updateScenario(scenario).updatePath(path))
                    }
                }
            } catch (contractException: ContractException) {
                Pair(null, contractException.failure().updatePath(path))
            }
        }
        return results
    }

    private fun failureResults(results: List<Pair<HttpStubData?, Result>>): Results =
        Results(results.map { it.second }.filterIsInstance<Result.Failure>().toMutableList())

    fun generateContractTests(suggestions: List<Scenario>): List<ContractTest> =
        generateContractTestScenarios(suggestions).map {
            ScenarioTest(it, resolverStrategies,
                it.sourceProvider, it.sourceRepository, it.sourceRepositoryBranch, it.specification, it.serviceType)
        }

    private fun getBadRequestsOrDefault(scenario: Scenario): BadRequestOrDefault? {
        val badRequestResponses = scenarios.filter {
            it.httpRequestPattern.httpPathPattern!!.path == scenario.httpRequestPattern.httpPathPattern!!.path
                    && it.httpResponsePattern.status.toString().startsWith("4")
        }.associate { it.httpResponsePattern.status to it.httpResponsePattern }

        val defaultResponse: HttpResponsePattern? = scenarios.find {
            it.httpRequestPattern.httpPathPattern!!.path == scenario.httpRequestPattern.httpPathPattern!!.path
                    && it.httpResponsePattern.status == DEFAULT_RESPONSE_CODE
        }?.httpResponsePattern

        if(badRequestResponses.isEmpty() && defaultResponse == null)
            return null

        return BadRequestOrDefault(badRequestResponses, defaultResponse)
    }

    fun generateContractTestScenarios(suggestions: List<Scenario>): List<Scenario> {
        return resolverStrategies.generation.let {
            it.positiveTestScenarios(this, suggestions) + it.negativeTestScenarios(this)
        }
    }

    fun positiveTestScenarios(suggestions: List<Scenario>) =
        scenarios.filter { it.isA2xxScenario() || it.examples.isNotEmpty() || it.isGherkinScenario }.map {
            it.newBasedOn(suggestions)
        }.flatMap {
            it.generateTestScenarios(resolverStrategies, testVariables, testBaseURLs)
        }

    fun negativeTestScenarios() =
        scenarios.filter {
            it.isA2xxScenario()
        }.flatMap { scenario ->
            val negativeScenario = scenario.negativeBasedOn(getBadRequestsOrDefault(scenario))

            val negativeTestScenarios = negativeScenario.generateTestScenarios(resolverStrategies, testVariables, testBaseURLs)

            negativeTestScenarios.filterNot { negativeTestScenario ->
                val sampleRequest = negativeTestScenario.httpRequestPattern.generate(negativeTestScenario.resolver)
                scenario.httpRequestPattern.matches(sampleRequest, scenario.resolver).isSuccess()
            }.mapIndexed { index, negativeScenario ->
                negativeScenario.copy(
                    generativePrefix = resolverStrategies.generation.negativePrefix,
                    disambiguate = { "[${(index + 1)}] " }
                )
            }
        }

    fun generateBackwardCompatibilityTestScenarios(): List<Scenario> =
        scenarios.flatMap { scenario ->
            scenario.copy(examples = emptyList()).generateBackwardCompatibilityScenarios()
        }

    fun matchingStub(
        scenarioStub: ScenarioStub,
        mismatchMessages: MismatchMessages = DefaultMismatchMessages
    ): HttpStubData =
        matchingStub(
            scenarioStub.request,
            scenarioStub.response,
            mismatchMessages
        ).copy(delayInSeconds = scenarioStub.delayInSeconds, requestBodyRegex = scenarioStub.requestBodyRegex?.let { Regex(it) }, stubToken = scenarioStub.stubToken)

    fun clearServerState() {
        serverState = emptyMap()
    }

    private fun combine(baseScenario: Scenario, newScenario: Scenario): Scenario {
        return convergeURLMatcher(baseScenario, newScenario).let { convergedScenario ->
            convergeHeaders(convergedScenario, newScenario)
        }.let { convergedScenario ->
            convergeQueryParameters(convergedScenario, newScenario)
        }.let { convergedScenario ->
            convergeRequestPayload(convergedScenario, newScenario)
        }.let { convergedScenario ->
            convergeResponsePayload(convergedScenario, newScenario)
        }
    }

    private fun convergeURLMatcher(baseScenario: Scenario, newScenario: Scenario): Scenario {
        if (baseScenario.httpRequestPattern.httpPathPattern!!.encompasses(
                newScenario.httpRequestPattern.httpPathPattern!!,
                baseScenario.resolver,
                newScenario.resolver
            ) is Result.Success
        )
            return baseScenario

        val basePathParts = baseScenario.httpRequestPattern.httpPathPattern.pathSegmentPatterns
        val newPathParts = newScenario.httpRequestPattern.httpPathPattern.pathSegmentPatterns

        val convergedPathPattern: List<URLPathSegmentPattern> = basePathParts.zip(newPathParts).map { (base, new) ->
            if(base.pattern.encompasses(new.pattern, baseScenario.resolver, newScenario.resolver) is Result.Success)
                base
            else {
                if(isInteger(base) && isInteger(new))
                    URLPathSegmentPattern(NumberPattern(), key = "id")
                else
                    throw ContractException("Can't figure out how to converge these URLs: ${baseScenario.httpRequestPattern.httpPathPattern.path}, ${newScenario.httpRequestPattern.httpPathPattern.path}")
            }
        }

        val convergedPath: String = convergedPathPattern.joinToString("/") {
            when (it.pattern) {
                is ExactValuePattern -> it.pattern.pattern.toStringLiteral()
                else -> "(${it.key}:${it.pattern.typeName})"
            }
        }.let { if(it.startsWith("/")) it else "/$it"}

        val convergedHttpPathPattern: HttpPathPattern = baseScenario.httpRequestPattern.httpPathPattern.copy(pathSegmentPatterns = convergedPathPattern, path = convergedPath)

        return baseScenario.copy(
            httpRequestPattern =  baseScenario.httpRequestPattern.copy(
                httpPathPattern = convergedHttpPathPattern
            )
        )
    }

    private fun convergeResponsePayload(baseScenario: Scenario, newScenario: Scenario): Scenario {
        val baseResponsePayload = baseScenario.httpResponsePattern.body
        val newResponsePayload = newScenario.httpResponsePattern.body

        return convergeDataStructure(baseResponsePayload, newResponsePayload, baseScenario.name) { converged ->
            baseScenario.copy(
                httpResponsePattern = baseScenario.httpResponsePattern.copy(
                    body = converged
                )
            )
        }
    }

    private fun convergeRequestPayload(baseScenario: Scenario, newScenario: Scenario): Scenario {
        if (baseScenario.httpRequestPattern.multiPartFormDataPattern.isNotEmpty())
            TODO("Multipart requests not yet supported")

        return if (baseScenario.httpRequestPattern.formFieldsPattern.size == 1) {
            if (newScenario.httpRequestPattern.formFieldsPattern.size != 1)
                throw ContractException("${baseScenario.httpRequestPattern.method} ${baseScenario.httpRequestPattern.httpPathPattern?.path} exists with different form fields")

            val baseRawPattern = baseScenario.httpRequestPattern.formFieldsPattern.values.first()
            val resolvedBasePattern = resolvedHop(baseRawPattern, baseScenario.resolver)

            val newRawPattern = newScenario.httpRequestPattern.formFieldsPattern.values.first()
            val resolvedNewPattern = resolvedHop(newRawPattern, newScenario.resolver)

            if (isObjectType(resolvedBasePattern) && !isObjectType(resolvedNewPattern))
                throw ContractException("${baseScenario.httpRequestPattern.method} ${baseScenario.httpRequestPattern.httpPathPattern?.path} exists with multiple payload types")

            val converged: Pattern = when {
                resolvedBasePattern.pattern is String && builtInPatterns.contains(resolvedBasePattern.pattern) -> {
                    if (resolvedBasePattern.pattern != resolvedNewPattern.pattern)
                        throw ContractException("Cannot converge ${baseScenario.httpRequestPattern.method} ${baseScenario.httpRequestPattern.httpPathPattern?.path} because there are multiple types of request payloads")

                    resolvedBasePattern
                }
                baseRawPattern is DeferredPattern -> {
                    if (baseRawPattern.pattern == newRawPattern.pattern && isObjectType(resolvedBasePattern))
                        baseRawPattern
                    else
                        throw ContractException("Cannot converge different types ${baseRawPattern.pattern} and ${newRawPattern.pattern} found in ${baseScenario.httpRequestPattern.method} ${baseScenario.httpRequestPattern.httpPathPattern?.path}")
                }
                else ->
                    TODO("Converging of type ${resolvedBasePattern.pattern} and ${resolvedNewPattern.pattern} in ${baseScenario.httpRequestPattern.method} ${baseScenario.httpRequestPattern.httpPathPattern?.path}")
            }

            baseScenario.copy(
                httpRequestPattern = baseScenario.httpRequestPattern.copy(
                    formFieldsPattern = mapOf(baseScenario.httpRequestPattern.formFieldsPattern.keys.first() to converged)
                )
            )
        } else if (baseScenario.httpRequestPattern.formFieldsPattern.isNotEmpty()) {
            TODO(
                "Form fields with non-json-object values (${
                    baseScenario.httpRequestPattern.formFieldsPattern.values.joinToString(
                        ", "
                    ) { it.typeAlias ?: if (it.pattern is String) it.pattern.toString() else it.typeName }
                })"
            )
        } else {
            val baseRequestBody = baseScenario.httpRequestPattern.body
            val newRequestBody = newScenario.httpRequestPattern.body

            convergeDataStructure(baseRequestBody, newRequestBody, baseScenario.name) { converged ->
                baseScenario.copy(
                    httpRequestPattern = baseScenario.httpRequestPattern.copy(
                        body = converged
                    )
                )
            }
        }
    }

    private fun convergeDataStructure(
        basePayload: Pattern,
        newPayload: Pattern,
        scenarioName: String,
        updateConverged: (Pattern) -> Scenario
    ): Scenario {
        return updateConverged(converge(basePayload, newPayload, scenarioName))
    }

    private fun converge(
        basePayload: Pattern,
        newPayload: Pattern,
        scenarioName: String,
    ): Pattern {
        return if (basePayload is TabularPattern && newPayload is TabularPattern) {
            TabularPattern(convergePatternMap(basePayload.pattern, newPayload.pattern))
        } else if (basePayload is ListPattern && newPayload is JSONArrayPattern) {
            val convergedNewPattern: Pattern = newPayload.pattern.fold(basePayload.pattern) { acc, newPattern ->
                converge(acc, newPattern, scenarioName)
            }

            ListPattern(convergedNewPattern)
        } else if (basePayload is ListPattern && newPayload is ListPattern) {
            val convergedNewPattern: Pattern = converge(basePayload.pattern, newPayload.pattern, scenarioName)

            ListPattern(convergedNewPattern)
        } else if (bothAreIdenticalDeferredPatterns(basePayload, newPayload)) {
            basePayload
        } else if (bothAreTheSamePrimitive(basePayload, newPayload)) {
            basePayload
        } else {
            throw ContractException("Payload definitions could not be converged (seen in Scenario named ${scenarioName}: ${basePayload.typeAlias ?: basePayload.typeName}, ${newPayload.typeAlias ?: newPayload.typeName})")
        }
    }

    private fun bothAreTheSamePrimitive(
        baseRequestBody: Pattern,
        newRequestBody: Pattern
    ) =
        (baseRequestBody is EmptyStringPattern && newRequestBody is EmptyStringPattern)
                || (baseRequestBody.pattern is String
                && builtInPatterns.contains(baseRequestBody.pattern as String)
                && newRequestBody.pattern is String
                && builtInPatterns.contains(newRequestBody.pattern as String)
                && baseRequestBody.pattern == newRequestBody.pattern)

    private fun bothAreIdenticalDeferredPatterns(
        baseRequestBody: Pattern,
        newRequestBody: Pattern
    ) =
        baseRequestBody is DeferredPattern && newRequestBody is DeferredPattern && baseRequestBody.pattern == newRequestBody.pattern

    private fun convergeQueryParameters(baseScenario: Scenario, newScenario: Scenario): Scenario {
        val baseQueryParams = baseScenario.httpRequestPattern.httpQueryParamPattern.queryPatterns
        val newQueryParams = newScenario.httpRequestPattern.httpQueryParamPattern.queryPatterns

        val convergedQueryParams = convergePatternMap(baseQueryParams, newQueryParams)

        return baseScenario.copy(
            httpRequestPattern = baseScenario.httpRequestPattern.copy(
                httpQueryParamPattern = baseScenario.httpRequestPattern.httpQueryParamPattern.copy(queryPatterns = convergedQueryParams)
            )
        )
    }

    private fun convergeHeaders(baseScenario: Scenario, newScenario: Scenario): Scenario {
        val baseRequestHeaders = baseScenario.httpRequestPattern.headersPattern.pattern
        val newRequestHeaders = newScenario.httpRequestPattern.headersPattern.pattern
        val convergedRequestHeaders = convergePatternMap(baseRequestHeaders, newRequestHeaders)

        val baseResponseHeaders = baseScenario.httpResponsePattern.headersPattern.pattern
        val newResponseHeaders = newScenario.httpResponsePattern.headersPattern.pattern
        val convergedResponseHeaders = convergePatternMap(baseResponseHeaders, newResponseHeaders)

        return baseScenario.copy(
            httpRequestPattern = baseScenario.httpRequestPattern.copy(
                headersPattern = HttpHeadersPattern(convergedRequestHeaders)
            ),
            httpResponsePattern = baseScenario.httpResponsePattern.copy(
                headersPattern = HttpHeadersPattern(convergedResponseHeaders)
            )
        )
    }

    private fun toOpenAPIURLPrefixMap(urls: List<String>): Map<String, String> {
        val normalisedURL = urls.map { url ->
            val path =
                url.removeSuffix("/").removePrefix("http://").removePrefix("https://").split("/").joinToString("/") {
                    if (it.toIntOrNull() != null)
                        "1"
                    else
                        it
                }
            path.let { if(it.startsWith("/")) it else "/$it"}
        }.distinct()

        val minLength = normalisedURL.minOfOrNull {
            it.split("/").size
        } ?: throw ContractException("No schema namespaces found")

        val segmentCount = 1.until(minLength + 1).first { length ->
            val segments = normalisedURL.map { url ->
                url.split("/").filterNot { it.isEmpty() }.takeLast(length).joinToString("_")
            }

            segments.toSet().size == urls.size
        }

        val prefixes = normalisedURL.map { url ->
            url.split("/").filterNot { it.isEmpty() }.takeLast(segmentCount).joinToString("_") { it.capitalizeFirstChar() }
        }

        return urls.zip(prefixes).toMap()
    }

    fun toOpenApi(): OpenAPI {
        val openAPI = OpenAPI()
        openAPI.info = Info().also {
            it.title = this.name
            it.version = "1"
        }

        scenarios.find { it.httpRequestPattern.method == null }?.let {
            throw ContractException("Scenario ${it.name} has no method")
        }

        scenarios.find { it.httpRequestPattern.httpPathPattern == null }?.let {
            throw ContractException("Scenario ${it.name} has no path")
        }

        fun normalize(url: String): String = url.replace('{', '_').replace('}', '_').split("/").joinToString("/") {
            if(it.toIntOrNull() != null)
                "1"
            else
                it
        }.let { if(it.startsWith("/")) it else "/$it"}

        val urlPrefixMap = toOpenAPIURLPrefixMap(scenarios.mapNotNull {
            it.httpRequestPattern.httpPathPattern?.path
        }.map {
            normalize(it)
        }.toSet().toList())

        val payloadAdjustedScenarios: List<Scenario> = scenarios.map { rawScenario ->
            val prefix = urlPrefixMap.getValue(normalize(rawScenario.httpRequestPattern.httpPathPattern?.path!!))

            var scenario = rawScenario

            if (scenario.httpRequestPattern.body.let {
                    it is DeferredPattern && it.pattern == "(RequestBody)" && isJSONPayload(
                        it.resolvePattern(scenario.resolver)
                    )
                }) {
                val requestBody = scenario.httpRequestPattern.body as DeferredPattern
                val oldTypeName = requestBody.pattern
                val newTypeName = "(${prefix}_${withoutPatternDelimiters(oldTypeName)})"
                val newRequestBody = requestBody.copy(pattern = newTypeName)

                val type = scenario.patterns.getValue(oldTypeName)
                val newTypes = scenario.patterns.minus(oldTypeName).plus(newTypeName to type)

                scenario = scenario.copy(
                    patterns = newTypes,
                    httpRequestPattern = scenario.httpRequestPattern.copy(
                        body = newRequestBody,
                        httpPathPattern = toPathPatternWithId(scenario.httpRequestPattern.httpPathPattern)
                    )
                )
            }

            if (scenario.httpResponsePattern.body.let {
                    it is DeferredPattern && it.pattern == "(ResponseBody)" && isJSONPayload(
                        it.resolvePattern(scenario.resolver)
                    )
                }) {
                val responseBody = scenario.httpResponsePattern.body as DeferredPattern
                val oldTypeName = responseBody.pattern
                val newTypeName = "(${prefix}_${withoutPatternDelimiters(oldTypeName)})"
                val newResponseBody = responseBody.copy(pattern = newTypeName)

                val type = scenario.patterns.getValue(oldTypeName)
                val newTypes = scenario.patterns.minus(oldTypeName).plus(newTypeName to type)

                scenario = scenario.copy(
                    patterns = newTypes,
                    httpResponsePattern = scenario.httpResponsePattern.copy(
                        body = newResponseBody
                    )
                )
            }

            scenario
        }

        val rawCombinedScenarios = payloadAdjustedScenarios.fold(emptyList<Scenario>()) { acc, payloadAdjustedScenario ->
            val scenarioWithSameURLAndPath = acc.find { alreadyCombinedScenario: Scenario ->
                similarURLPath(alreadyCombinedScenario, payloadAdjustedScenario)
                        && alreadyCombinedScenario.httpRequestPattern.method == payloadAdjustedScenario.httpRequestPattern.method
                        && alreadyCombinedScenario.httpResponsePattern.status == payloadAdjustedScenario.httpResponsePattern.status
            }

            if (scenarioWithSameURLAndPath == null)
                acc.plus(payloadAdjustedScenario)
            else {
                val combined = combine(scenarioWithSameURLAndPath, payloadAdjustedScenario)
                acc.minus(scenarioWithSameURLAndPath).plus(combined)
            }
        }

        val paths: List<Pair<String, PathItem>> = rawCombinedScenarios.fold(emptyList()) { acc, scenario ->
            val pathName = scenario.httpRequestPattern.httpPathPattern!!.toOpenApiPath()

            val existingPathItem = acc.find { it.first == pathName }?.second
            val pathItem = existingPathItem ?: PathItem()

            val operation = when (scenario.httpRequestPattern.method!!) {
                "GET" -> pathItem.get
                "POST" -> pathItem.post
                "PUT" -> pathItem.put
                "DELETE" -> pathItem.delete
                else -> TODO("Method \"${scenario.httpRequestPattern.method}\" in scenario ${scenario.name}")
            } ?: Operation().apply {
                this.summary = withoutQueryParams(scenario.name)
            }

            val pathParameters = scenario.httpRequestPattern.httpPathPattern.pathParameters()

            val openApiPathParameters = pathParameters.map {
                val pathParameter: Parameter = PathParameter()
                pathParameter.name = it.key
                pathParameter.schema = toOpenApiSchema(it.pattern)
                pathParameter
            }
            val queryParameters = scenario.httpRequestPattern.httpQueryParamPattern.queryPatterns
            val openApiQueryParameters = queryParameters.map { (key, pattern) ->
                val queryParameter: Parameter = QueryParameter()
                queryParameter.name = key.removeSuffix("?")
                queryParameter.schema = toOpenApiSchema(pattern)
                queryParameter
            }
            val openApiRequestHeaders = scenario.httpRequestPattern.headersPattern.pattern.map { (key, pattern) ->
                val headerParameter = HeaderParameter()
                headerParameter.name = key.removeSuffix("?")
                headerParameter.schema = toOpenApiSchema(pattern)
                headerParameter.required = key.contains("?").not()
                headerParameter
            }

            val requestBodyType = scenario.httpRequestPattern.body

            val requestBodySchema: Pair<String, MediaType>? = requestBodySchema(requestBodyType, scenario)

            if (requestBodySchema != null) {
                operation.requestBody = RequestBody().apply {
                    this.content = Content().apply {
                        this[requestBodySchema.first] = requestBodySchema.second
                    }
                }
            }

            operation.parameters = openApiPathParameters + openApiQueryParameters + openApiRequestHeaders

            val responses = operation.responses ?: ApiResponses()

            val apiResponse = ApiResponse()

            apiResponse.description = withoutQueryParams(scenario.name)

            val openApiResponseHeaders = scenario.httpResponsePattern.headersPattern.pattern.map { (key, pattern) ->
                val header = Header()
                header.schema = toOpenApiSchema(pattern)
                header.required = !key.endsWith("?")

                Pair(withoutOptionality(key), header)
            }.toMap()

            if (openApiResponseHeaders.isNotEmpty()) {
                apiResponse.headers = openApiResponseHeaders
            }

            if (scenario.httpResponsePattern.body !is EmptyStringPattern) {
                apiResponse.content = Content().apply {
                    val responseBodyType = scenario.httpResponsePattern.body

                    val responseBodySchema: Pair<String, MediaType> = when {
                        isJSONPayload(responseBodyType) || responseBodyType is DeferredPattern && isJSONPayload(
                            responseBodyType.resolvePattern(scenario.resolver)
                        ) -> {
                            jsonMediaType(responseBodyType)
                        }
                        responseBodyType is XMLPattern || responseBodyType is DeferredPattern && responseBodyType.resolvePattern(
                            scenario.resolver
                        ) is XMLPattern -> {
                            throw ContractException("XML not supported yet")
                        }
                        else -> {
                            val mediaType = MediaType()
                            mediaType.schema = toOpenApiSchema(responseBodyType)
                            Pair("text/plain", mediaType)
                        }
                    }

                    this.addMediaType(responseBodySchema.first, responseBodySchema.second)
                }
            }

            responses.addApiResponse(scenario.httpResponsePattern.status.toString(), apiResponse)

            operation.responses = responses

            when (scenario.httpRequestPattern.method) {
                "GET" -> pathItem.get = operation
                "POST" -> pathItem.post = operation
                "PUT" -> pathItem.put = operation
                "DELETE" -> pathItem.delete = operation
            }

            acc.plus(pathName to pathItem)
        }

        val schemas: Map<String, Pattern> = payloadAdjustedScenarios.map {
            it.patterns.entries
        }.flatten().fold(emptyMap<String, Pattern>()) { acc, entry ->
            val key = withoutPatternDelimiters(entry.key)

            if (acc.contains(key) && isObjectType(acc.getValue(key))) {
                val converged: Map<String, Pattern> = objectStructure(acc.getValue(key))
                val new: Map<String, Pattern> = objectStructure(entry.value)

                acc.plus(key to TabularPattern(convergePatternMap(converged, new)))
            } else {
                acc.plus(key to entry.value)
            }
        }.mapKeys {
            withoutPatternDelimiters(it.key)
        }

        if (schemas.isNotEmpty()) {
            openAPI.components = Components()
            openAPI.components.schemas = schemas.mapValues {
                toOpenApiSchema(it.value)
            }
        }

        openAPI.paths = Paths().also {
            paths.forEach { (pathName, newPath) ->
                it.addPathItem(pathName, newPath)
            }
        }

        return openAPI
    }

    private fun withoutQueryParams(name: String): String {
        return name.replace(Regex("""\?.*$"""), "")
    }

    private fun toPathPatternWithId(httpPathPattern: HttpPathPattern?): HttpPathPattern {
        if(httpPathPattern!!.pathSegmentPatterns.any { it.pattern !is ExactValuePattern })
            return httpPathPattern

        val pathSegmentPatternsWithIds: List<URLPathSegmentPattern> = httpPathPattern.pathSegmentPatterns.map { type ->
            if(isInteger(type))
                URLPathSegmentPattern(NumberPattern(), key = "id")
            else
                type
        }

        val pathWithIds: String = pathSegmentPatternsWithIds.joinToString("/") {
            when (it.pattern) {
                is ExactValuePattern -> it.pattern.pattern.toStringLiteral()
                else -> "(${it.key}:${it.pattern.typeName})"
            }
        }.let { if(it.startsWith("/")) it else "/$it"}

        return httpPathPattern.copy(pathSegmentPatterns = pathSegmentPatternsWithIds, path = pathWithIds)
    }

    private fun requestBodySchema(
        requestBodyType: Pattern,
        scenario: Scenario
    ): Pair<String, MediaType>? = when {
        requestBodyType is LookupRowPattern -> {
            requestBodySchema(requestBodyType.pattern, scenario)
        }
        isJSONPayload(requestBodyType) || requestBodyType is DeferredPattern && isJSONPayload(
            requestBodyType.resolvePattern(
                scenario.resolver
            )
        ) -> {
            jsonMediaType(requestBodyType)
        }
        requestBodyType is XMLPattern || requestBodyType is DeferredPattern && requestBodyType.resolvePattern(scenario.resolver) is XMLPattern -> {
            throw ContractException("XML not supported yet")
        }
        requestBodyType is ExactValuePattern -> {
            val mediaType = MediaType()
            mediaType.schema = toOpenApiSchema(requestBodyType)
            Pair("text/plain", mediaType)
        }
        requestBodyType.pattern.let { it is String && builtInPatterns.contains(it) } -> {
            val mediaType = MediaType()
            mediaType.schema = toOpenApiSchema(requestBodyType)
            Pair("text/plain", mediaType)
        }
        else -> {
            if (scenario.httpRequestPattern.formFieldsPattern.isNotEmpty()) {
                val mediaType = MediaType()
                mediaType.schema = Schema<Any>().apply {
                    this.required = scenario.httpRequestPattern.formFieldsPattern.keys.toList()
                    this.properties = scenario.httpRequestPattern.formFieldsPattern.map { (key, type) ->
                        val schema = toOpenApiSchema(type)
                        Pair(withoutOptionality(key), schema)
                    }.toMap()
                }

                val encoding: MutableMap<String, Encoding> =
                    scenario.httpRequestPattern.formFieldsPattern.map { (key, type) ->
                        when {
                            isJSONPayload(type) || (type is DeferredPattern && isJSONPayload(
                                type.resolvePattern(
                                    scenario.resolver
                                )
                            )) -> {
                                val encoding = Encoding().apply {
                                    this.contentType = "application/json"
                                }

                                Pair(withoutOptionality(key), encoding)
                            }
                            type is XMLPattern ->
                                throw NotImplementedError("XML encoding not supported for form fields")
                            else -> {
                                null
                            }
                        }
                    }.filterNotNull().toMap().toMutableMap()

                if (encoding.isNotEmpty())
                    mediaType.encoding = encoding

                Pair("application/x-www-form-urlencoded", mediaType)
            } else if (scenario.httpRequestPattern.multiPartFormDataPattern.isNotEmpty()) {
                throw NotImplementedError("multipart form data not yet supported")
            } else {
                null
            }
        }
    }

    private fun jsonMediaType(requestBodyType: Pattern): Pair<String, MediaType> {
        val mediaType = MediaType()
        mediaType.schema = toOpenApiSchema(requestBodyType)
        return Pair("application/json", mediaType)
    }

    private fun cleanupDescriptor(descriptor: String): String {
        val withoutBrackets = withoutPatternDelimiters(descriptor)
        val modifiersTrimmed = withoutBrackets.trimEnd('*', '?')

        val (base, modifiers) = if (withoutBrackets == modifiersTrimmed)
            Pair(withoutBrackets, "")
        else {
            val modifiers = withoutBrackets.substring(modifiersTrimmed.length)
            Pair(modifiersTrimmed, modifiers)
        }

        return "${base.trim('_')}$modifiers"
    }

    private fun getTypeAndDescriptor(map: Map<String, Pattern>, key: String): Pair<String, Pattern> {
        val nonOptionalKey = withoutOptionality(key)
        val optionalKey = "$nonOptionalKey?"
        val commonValueType = map.getOrElse(nonOptionalKey) { map.getValue(optionalKey) }

        val descriptor = commonValueType.typeAlias
            ?: commonValueType.pattern.let { if (it is String) it else commonValueType.typeName }

        return Pair(descriptor, commonValueType)
    }

    private fun convergePatternMap(map1: Map<String, Pattern>, map2: Map<String, Pattern>): Map<String, Pattern> {
        val common: Map<String, Pattern> = map1.filter { entry ->
            val cleanedKey = withoutOptionality(entry.key)
            cleanedKey in map2 || "${cleanedKey}?" in map2
        }.mapKeys { entry ->
            val cleanedKey = withoutOptionality(entry.key)
            if (isOptional(entry.key) || "${cleanedKey}?" in map2) {
                "${cleanedKey}?"
            } else
                cleanedKey
        }.mapValues { entry ->
            val (type1Descriptor, type1) = getTypeAndDescriptor(map1, entry.key)
            val (type2Descriptor, type2) = getTypeAndDescriptor(map2, entry.key)

            if (type1Descriptor != type2Descriptor) {
                val typeDescriptors = listOf(type1Descriptor, type2Descriptor).sorted()
                val cleanedUpDescriptors = typeDescriptors.map { cleanupDescriptor(it) }

                if (isEmptyOrNull(type1) || isEmptyOrNull(type2)) {
                    val type = if (isEmptyOrNull(type1)) type2 else type1

                    if (type is DeferredPattern) {
                        val descriptor = if (isEmptyOrNull(type1)) type2Descriptor else type1Descriptor
                        val withoutBrackets = withoutPatternDelimiters(descriptor)
                        val newPattern = withoutBrackets.removeSuffix("?").let { "($it)" }

                        AnyPattern(listOf(NullPattern, type.copy(pattern = newPattern)))
                    } else {
                        AnyPattern(listOf(NullPattern, type))
                    }
                } else if (cleanedUpDescriptors.first() == cleanedUpDescriptors.second()) {
                    entry.value
                } else if (withoutPatternDelimiters(cleanedUpDescriptors.second()).trimEnd('?') == withoutPatternDelimiters(
                        cleanedUpDescriptors.first()
                    )
                ) {
                    val type: Pattern = listOf(map1, map2).map {
                        getTypeAndDescriptor(it, entry.key)
                    }.associate {
                        cleanupDescriptor(it.first) to it.second
                    }.getValue(cleanedUpDescriptors.second())

                    type
                } else {
                    logger.log("Found conflicting values for the same key ${entry.key} ($type1Descriptor, $type2Descriptor).")
                    entry.value
                }
            } else
                entry.value
        }

        val onlyInMap1: Map<String, Pattern> = map1.filter { entry ->
            val cleanedKey = withoutOptionality(entry.key)
            (cleanedKey !in common && "${cleanedKey}?" !in common)
        }.mapKeys { entry ->
            val cleanedKey = withoutOptionality(entry.key)
            "${cleanedKey}?"
        }

        val onlyInMap2: Map<String, Pattern> = map2.filter { entry ->
            val cleanedKey = withoutOptionality(entry.key)
            (cleanedKey !in common && "${cleanedKey}?" !in common)
        }.mapKeys { entry ->
            val cleanedKey = withoutOptionality(entry.key)
            "${cleanedKey}?"
        }

        return common.plus(onlyInMap1).plus(onlyInMap2)
    }

    private fun objectStructure(objectType: Pattern): Map<String, Pattern> {
        return when (objectType) {
            is TabularPattern -> objectType.pattern
            is JSONObjectPattern -> objectType.pattern
            else -> throw ContractException("Unrecognized type ${objectType.typeName}")
        }
    }

    private fun isObjectType(type: Pattern): Boolean = type is TabularPattern || type is JSONObjectPattern

    private fun isJSONPayload(type: Pattern) =
        type is TabularPattern || type is JSONObjectPattern || type is JSONArrayPattern

    private fun toOpenApiSchema(pattern: Pattern): Schema<Any> {
        val schema = when {
            pattern is DictionaryPattern -> {
                ObjectSchema().apply {
                    additionalProperties = Schema<Any>().apply {
                        this.`$ref` = withoutPatternDelimiters(pattern.valuePattern.pattern.toString())
                    }
                }
            }
            pattern is LookupRowPattern -> toOpenApiSchema(pattern.pattern)
            pattern is TabularPattern -> tabularToSchema(pattern)
            pattern is JSONObjectPattern -> jsonObjectToSchema(pattern)
            isArrayOfNullables(pattern) -> {
                ArraySchema().apply {
                    val typeAlias =
                        ((pattern as ListPattern).pattern as AnyPattern).pattern.first { !isEmptyOrNull(it) }.let {
                            if (it.pattern is String && builtInPatterns.contains(it.pattern.toString()))
                                it.pattern as String
                            else
                                it.typeAlias?.let { typeAlias ->
                                    if (!typeAlias.startsWith("("))
                                        "($typeAlias)"
                                    else
                                        typeAlias
                                } ?: throw ContractException("Unknown type: $it")
                        }

                    val arrayItemSchema = getSchemaType(typeAlias)

                    this.items = nullableSchemaAsOneOf(arrayItemSchema)
                }
            }
            isArrayOrNull(pattern) -> {
                ArraySchema().apply {
                    pattern as AnyPattern

                    this.items =
                        getSchemaType(pattern.pattern.first { !isEmptyOrNull(it) }.let {
                            listInnerTypeDescriptor(it as ListPattern)
                        })

                    this.nullable = true
                }
            }
            isNullableDeferred(pattern) -> {
                pattern as AnyPattern

                val innerPattern: Pattern = pattern.pattern.first { !isEmptyOrNull(it) }
                innerPattern as DeferredPattern

                val typeSchema = Schema<Any>().apply {
                    this.`$ref` = withoutPatternDelimiters(innerPattern.pattern)
                }

                nullableSchemaAsOneOf(typeSchema)
            }
            isNullable(pattern) -> {
                pattern as AnyPattern

                val innerPattern: Pattern = pattern.pattern.first { !isEmptyOrNull(it) }

                when {
                    innerPattern.pattern is String && innerPattern.pattern in builtInPatterns -> toOpenApiSchema(
                        builtInPatterns.getValue(innerPattern.pattern as String)
                    )
                    else -> toOpenApiSchema(innerPattern)
                }.apply {
                    this.nullable = true
                }
            }
            pattern is ListPattern -> {
                if (pattern.pattern is DeferredPattern) {
                    ArraySchema().apply {
                        this.items = getSchemaType(pattern.pattern.typeAlias)
                    }
                } else if (isArrayOfNullables(pattern)) {
                    ArraySchema().apply {
                        val innerPattern: Pattern = (pattern.pattern as AnyPattern).pattern.first { it !is NullPattern }
                        this.items = nullableSchemaAsOneOf(toOpenApiSchema(innerPattern))
                    }
                } else {
                    ArraySchema().apply {
                        this.items = toOpenApiSchema(pattern.pattern)
                    }
                }
            }
            pattern is NumberPattern || (pattern is DeferredPattern && pattern.pattern == "(number)") -> NumberSchema()
            pattern is BooleanPattern || (pattern is DeferredPattern && pattern.pattern == "(boolean)") -> BooleanSchema()
            pattern is DateTimePattern || (pattern is DeferredPattern && pattern.pattern == "(datetime)") -> StringSchema()
            pattern is StringPattern || pattern is EmptyStringPattern || (pattern is DeferredPattern && pattern.pattern == "(string)") || (pattern is DeferredPattern && pattern.pattern == "(nothing)") -> StringSchema()
            pattern is NullPattern || (pattern is DeferredPattern && pattern.pattern == "(null)") -> Schema<Any>().apply {
                this.nullable = true
            }
            pattern is DeferredPattern -> Schema<Any>().apply {
                this.`$ref` = withoutPatternDelimiters(pattern.pattern)
            }
            pattern is JSONArrayPattern && pattern.pattern.isEmpty() ->
                ArraySchema().apply {
                    this.items = StringSchema()
                }
            pattern is JSONArrayPattern && pattern.pattern.isNotEmpty() -> {
                if (pattern.pattern.all { it == pattern.pattern.first() })
                    ArraySchema().apply {
                        this.items = toOpenApiSchema(pattern.pattern.first())
                    }
                else
                    throw ContractException("Conversion of raw JSON array type to OpenAPI is not supported. Change the contract spec to define a type and use (type*) instead of a JSON array.")
            }
            pattern is ExactValuePattern -> {
                toOpenApiSchema(pattern.pattern.type()).apply {
                    this.enum = listOf(pattern.pattern.toStringLiteral())
                }
            }
            pattern is PatternInStringPattern -> {
                StringSchema()
            }
            pattern is AnyPattern && pattern.pattern.map { it.javaClass }.distinct().size == 1 && pattern.pattern.filterIsInstance<ExactValuePattern>().map { it.pattern }.filterIsInstance<ScalarValue>().isNotEmpty() && pattern.pattern.first() is ExactValuePattern -> {
                val specmaticType = (pattern.pattern.first() as ExactValuePattern).pattern.type()
                val values = pattern.pattern.filterIsInstance<ExactValuePattern>().map { it.pattern }.filterIsInstance<ScalarValue>().map { it.nativeValue }

                toOpenApiSchema(specmaticType).also {
                    it.enum = values
                }
            }
            pattern is QueryParameterScalarPattern -> {
                toOpenApiSchema(pattern.pattern)
            }
            else ->
                TODO("Not supported: ${pattern.typeAlias ?: pattern.typeName}, ${pattern.javaClass.name}")
        }

        return schema as Schema<Any>
    }

    private fun nullableSchemaAsOneOf(typeSchema: Schema<Any>): ComposedSchema {
        val nullableSchema = Schema<Any>().apply {
            this.nullable = true
            this.properties = emptyMap()
        }

        return ComposedSchema().apply {
            this.oneOf = listOf(nullableSchema, typeSchema)
        }
    }

    private fun listInnerTypeDescriptor(it: ListPattern): String {
        return it.pattern.typeAlias
            ?: when (val innerPattern = it.pattern.pattern) {
                is String -> innerPattern
                else -> throw ContractException("Type alias not found for type ${it.typeName}")
            }
    }

    private fun isNullableDeferred(pattern: Pattern): Boolean {
        return isNullable(pattern) && pattern is AnyPattern && pattern.pattern.first { it.pattern != "(empty)" && it.pattern != "(null)" }
            .let {
                it is DeferredPattern && withPatternDelimiters(
                    withoutPatternDelimiters(it.pattern).removeSuffix("*").removeSuffix("?").removeSuffix("*")
                ) !in builtInPatterns
            }
    }

    private fun getSchemaType(type: String): Schema<Any> {
        return if (builtInPatterns.contains(type)) {
            toOpenApiSchema(builtInPatterns.getValue(type))
        }
        else {
            val cleanedUpType = withoutPatternDelimiters(type)

            Schema<Any>().also { it.`$ref` = cleanedUpType }
        }
    }

    private fun isArrayOrNull(pattern: Pattern): Boolean =
        isNullable(pattern) && pattern is AnyPattern && pattern.pattern.first { !isEmptyOrNull(it) } is ListPattern

    private fun isArrayOfNullables(pattern: Pattern) =
        pattern is ListPattern && pattern.pattern is AnyPattern && isNullable(pattern.pattern)

    private fun isEmptyOrNull(pattern: Pattern): Boolean {
        return when (pattern) {
            is DeferredPattern -> pattern.typeAlias in listOf("(empty)", "(null)")
            is LookupRowPattern -> isEmptyOrNull(pattern.pattern)
            else -> pattern in listOf(EmptyStringPattern, NullPattern)
        }
    }

    private fun isNullable(pattern: Pattern) =
        pattern is AnyPattern && pattern.pattern.any { isEmptyOrNull(it) }

    private fun jsonObjectToSchema(pattern: JSONObjectPattern): Schema<Any> = jsonToSchema(pattern.pattern)
    private fun tabularToSchema(pattern: TabularPattern): Schema<Any> = jsonToSchema(pattern.pattern)

    private fun jsonToSchema(pattern: Map<String, Pattern>): Schema<Any> {
        val schema = Schema<Any>()

        schema.required = pattern.keys.filterNot { it.endsWith("?") }

        val properties: Map<String, Schema<Any>> = pattern.mapValues { (_, valueType) ->
            toOpenApiSchema(valueType)
        }.mapKeys { withoutOptionality(it.key) }

        schema.properties = properties

        return schema
    }

    fun useExamples(externalisedJSONExamples: Map<OpenApiSpecification.OperationIdentifier, List<Row>>): Feature {
        val scenariosWithExamples: List<Scenario> = scenarios.map {
            it.useExamples(externalisedJSONExamples)
        }

        return this.copy(scenarios = scenariosWithExamples)
    }

    private fun loadExternalisedJSONExamples(testsDirectory: File?): Map<OpenApiSpecification.OperationIdentifier, List<Row>> {
        if (testsDirectory == null)
            return emptyMap()

        if (!testsDirectory.exists())
            return emptyMap()

        val files = testsDirectory.listFiles()

        if (files.isNullOrEmpty())
            return emptyMap()

        return files.map { ExampleFromFile(it) }.mapNotNull { exampleFromFile ->
            try {
                with(exampleFromFile) {
                    logger.log("Loading test file ${exampleFromFile.expectationFilePath}")

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

                    OpenApiSpecification.OperationIdentifier(requestMethod, requestPath, responseStatus) to Row(
                        columnNames,
                        values,
                        name = testName,
                        fileSource = exampleFromFile.file.canonicalPath
                    )
                }
            } catch (e: Throwable) {
                logger.log(e, "Error reading file ${exampleFromFile.expectationFilePath}")
                null
            }
        }
            .groupBy { (operationIdentifier, _) -> operationIdentifier }
            .mapValues { (_, value) -> value.map { it.second } }
    }

    fun loadExternalisedExamples(): Feature {
        val testsDirectory = getTestsDirectory(File(this.path))
        val externalisedJSONExamples = loadExternalisedJSONExamples(testsDirectory)

        if(externalisedJSONExamples.isEmpty())
            return this

        val featureWithExternalisedExamples = useExamples(externalisedJSONExamples)

        val externalizedExampleFilePaths =
            externalisedJSONExamples.entries.flatMap { (_, rows) ->
                rows.map {
                    it.fileSource
                }
            }.filterNotNull().sorted().toSet()

        val utilizedFileSources =
            featureWithExternalisedExamples.scenarios.asSequence().flatMap { scenarioInfo ->
                scenarioInfo.examples.flatMap { examples ->
                    examples.rows.map {
                        it.fileSource
                    }
                }
            }.filterNotNull()
                .sorted().toSet()

        val unusedExternalizedExamples = (externalizedExampleFilePaths - utilizedFileSources)
        if (unusedExternalizedExamples.isNotEmpty()) {
            logger.log("The following externalized examples were not used:")

            unusedExternalizedExamples.sorted().forEach {
                logger.log("  $it")
            }
        }

        return featureWithExternalisedExamples
    }

    private fun testDirectoryFileFromEnvironmentVariable(): File? {
        return readEnvVarOrProperty(testDirectoryEnvironmentVariable, testDirectoryProperty)?.let {
            File(System.getenv(testDirectoryEnvironmentVariable))
        }
    }

    private fun testDirectoryFileFromSpecificationPath(openApiFilePath: String): File? {
        if (openApiFilePath.isBlank())
            return null

        return File(openApiFilePath).canonicalFile.let {
            it.parentFile.resolve(it.nameWithoutExtension + "_tests")
        }
    }


    private fun getTestsDirectory(contractFile: File): File? {
        val testDirectory = testDirectoryFileFromSpecificationPath(contractFile.path) ?: testDirectoryFileFromEnvironmentVariable()

        return when {
            testDirectory?.exists() == true -> {
                logger.log("Test directory ${testDirectory.canonicalPath} found")
                testDirectory
            }

            else -> {
                null
            }
        }
    }
}

class EmptyContract : Throwable()

private fun toFixtureInfo(rest: String): Pair<String, Value> {
    val fixtureTokens = breakIntoPartsMaxLength(rest.trim(), 2)

    if (fixtureTokens.size != 2)
        throw ContractException("Couldn't parse fixture data: $rest")

    return Pair(fixtureTokens[0], toFixtureData(fixtureTokens[1]))
}

private fun toFixtureData(rawData: String): Value = parsedJSON(rawData)

internal fun stringOrDocString(string: String?, step: StepInfo): String {
    val trimmed = string?.trim() ?: ""
    return trimmed.ifEmpty { step.docString }
}

private fun toPatternInfo(step: StepInfo, rowsList: List<TableRow>): Pair<String, Pattern> {
    val tokens = breakIntoPartsMaxLength(step.rest, 2)

    val patternName = withPatternDelimiters(tokens[0])

    val patternDefinition = stringOrDocString(tokens.getOrNull(1), step)

    val pattern = when {
        patternDefinition.isEmpty() -> rowsToTabularPattern(rowsList, typeAlias = patternName)
        else -> parsedPattern(patternDefinition, typeAlias = patternName)
    }

    return Pair(patternName, pattern)
}

private fun toFacts(rest: String, fixtures: Map<String, Value>): Map<String, Value> {
    return try {
        jsonStringToValueMap(rest)
    } catch (notValidJSON: Exception) {
        val factTokens = breakIntoPartsMaxLength(rest, 2)
        val name = factTokens[0]
        val data = factTokens.getOrNull(1)?.let { StringValue(it) } ?: fixtures.getOrDefault(name, True)

        mapOf(name to data)
    }
}

private fun lexScenario(
    steps: List<Step>,
    examplesList: List<Examples>,
    featureTags: List<Tag>,
    backgroundScenarioInfo: ScenarioInfo?,
    filePath: String,
    includedSpecifications: List<IncludedSpecification?>
): ScenarioInfo {
    val filteredSteps =
        steps.map { step -> StepInfo(step.text, listOfDatatableRows(step), step) }.filterNot { it.isEmpty }

    val parsedScenarioInfo = filteredSteps.fold(backgroundScenarioInfo ?: ScenarioInfo(httpRequestPattern = HttpRequestPattern())) { scenarioInfo, step ->
        when (step.keyword) {
            in HTTP_METHODS -> {
                step.words.getOrNull(1)?.let {
                    val urlInSpec = step.rest
                    val pathParamPattern = try {
                        buildHttpPathPattern(URI.create(urlInSpec))
                    } catch (e: Throwable) {
                        throw Exception(
                            "Could not parse the contract URL \"${step.rest}\" in scenario \"${scenarioInfo.scenarioName}\"",
                            e
                        )
                    }

                    val queryParamPattern = buildQueryPattern(URI.create(urlInSpec))

                    scenarioInfo.copy(
                        httpRequestPattern = scenarioInfo.httpRequestPattern.copy(
                            httpPathPattern = pathParamPattern,
                            httpQueryParamPattern = queryParamPattern,
                            method = step.keyword.uppercase()
                        )
                    )
                } ?: throw ContractException("Line ${step.line}: $step.text")
            }
            "REQUEST-HEADER" ->
                scenarioInfo.copy(
                    httpRequestPattern = scenarioInfo.httpRequestPattern.copy(
                        headersPattern = plusHeaderPattern(
                            step.rest,
                            scenarioInfo.httpRequestPattern.headersPattern
                        )
                    )
                )
            "RESPONSE-HEADER" ->
                scenarioInfo.copy(
                    httpResponsePattern = scenarioInfo.httpResponsePattern.copy(
                        headersPattern = plusHeaderPattern(
                            step.rest,
                            scenarioInfo.httpResponsePattern.headersPattern
                        )
                    )
                )
            "STATUS" ->
                scenarioInfo.copy(
                    httpResponsePattern = scenarioInfo.httpResponsePattern.copy(
                        status = Integer.valueOf(
                            step.rest
                        )
                    )
                )
            "REQUEST-BODY" ->
                scenarioInfo.copy(httpRequestPattern = scenarioInfo.httpRequestPattern.copy(body = toPattern(step)))
            "RESPONSE-BODY" ->
                scenarioInfo.copy(httpResponsePattern = scenarioInfo.httpResponsePattern.bodyPattern(toPattern(step)))
            "FACT" ->
                scenarioInfo.copy(
                    expectedServerState = scenarioInfo.expectedServerState.plus(
                        toFacts(
                            step.rest,
                            scenarioInfo.fixtures
                        )
                    )
                )
            "TYPE", "PATTERN", "JSON" ->
                scenarioInfo.copy(patterns = scenarioInfo.patterns.plus(toPatternInfo(step, step.rowsList)))
            "ENUM" ->
                scenarioInfo.copy(patterns = scenarioInfo.patterns.plus(parseEnum(step)))
            "FIXTURE" ->
                scenarioInfo.copy(fixtures = scenarioInfo.fixtures.plus(toFixtureInfo(step.rest)))
            "FORM-FIELD" ->
                scenarioInfo.copy(
                    httpRequestPattern = scenarioInfo.httpRequestPattern.copy(
                        formFieldsPattern = plusFormFields(
                            scenarioInfo.httpRequestPattern.formFieldsPattern,
                            step.rest,
                            step.rowsList
                        )
                    )
                )
            "REQUEST-PART" ->
                scenarioInfo.copy(
                    httpRequestPattern = scenarioInfo.httpRequestPattern.copy(
                        multiPartFormDataPattern = scenarioInfo.httpRequestPattern.multiPartFormDataPattern.plus(
                            toFormDataPart(step, filePath)
                        )
                    )
                )
            "VALUE" ->
                scenarioInfo.copy(
                    references = values(
                        step.rest,
                        scenarioInfo.references,
                        backgroundScenarioInfo?.references ?: emptyMap(),
                        filePath
                    )
                )
            "EXPORT" ->
                scenarioInfo.copy(
                    bindings = setters(
                        step.rest,
                        backgroundScenarioInfo?.bindings ?: emptyMap(),
                        scenarioInfo.bindings
                    )
                )
            else -> {
                val location = when (step.raw.location) {
                    null -> ""
                    else -> " at line ${step.raw.location.line}"
                }

                throw ContractException("""Invalid syntax$location: ${step.raw.keyword.trim()} ${step.raw.text} -> keyword "${step.originalKeyword}" not recognised.""")
            }
        }
    }

    val tags = featureTags.map { tag -> tag.name }
    val ignoreFailure = when {
        tags.asSequence().map { it.uppercase() }.contains("@WIP") -> true
        else -> false
    }

    val scenarioInfo = if (includedSpecifications.isEmpty() || backgroundScenarioInfo == null) {
        scenarioInfoWithExamples(
            parsedScenarioInfo,
            backgroundScenarioInfo ?: ScenarioInfo(),
            examplesList,
            ignoreFailure
        )
    } else {
        val matchingScenarios: List<ScenarioInfo> = includedSpecifications.mapNotNull {
            it?.matches(parsedScenarioInfo, steps).orEmpty()
        }.flatten()

        if (matchingScenarios.size > 1) throw ContractException("Scenario: ${parsedScenarioInfo.scenarioName} is not specific, it matches ${matchingScenarios.size} in the included Wsdl / OpenApi")

        val matchingScenario = matchingScenarios.first().copy(bindings = parsedScenarioInfo.bindings)

        scenarioInfoWithExamples(matchingScenario, backgroundScenarioInfo, examplesList, ignoreFailure)
    }

    return scenarioInfo.copy(isGherkinScenario = true)
}

private fun listOfDatatableRows(it: Step) = it.dataTable?.rows ?: mutableListOf()

fun parseEnum(step: StepInfo): Pair<String, Pattern> {
    val tokens = step.text.split(" ")

    if (tokens.size < 5)
        throw ContractException("Enum syntax error in step at line ${step.raw.location.line}. Syntax should be Given(/When/Then) enum EnumName <TypeName> values choice1,choice2,choice3")
    val enumName = tokens[1]
    val enumValues = tokens[4].split(",")
    val enumType = tokens[2]
    val exactValuePatterns = enumValues.map { enumValue ->
        val enumPattern = parsedPattern(enumType).run {
            when (this) {
                is DeferredPattern -> this.resolvePattern(Resolver())
                is AnyPattern -> throw ContractException("Enums $enumName type $enumType cannot be nullable. To mark the enum nullable please use it with nullable syntax. Suggested Usage: (${enumName}?)")
                else -> this
            }
        }
        ExactValuePattern(
            when (enumPattern) {
                is StringPattern -> StringValue(enumValue)
                is NumberPattern -> NumberValue(enumValue.toInt())
                else -> throw ContractException("Enums can only be of type String or Number")
            }
        )
    }
    return Pair("($enumName)", AnyPattern(exactValuePatterns))
}

private fun scenarioInfoWithExamples(
    parsedScenarioInfo: ScenarioInfo,
    backgroundScenarioInfo: ScenarioInfo,
    examplesList: List<Examples>,
    ignoreFailure: Boolean
) = parsedScenarioInfo.copy(
    examples = backgroundScenarioInfo.examples.plus(examplesFrom(examplesList)),
    bindings = backgroundScenarioInfo.bindings.plus(parsedScenarioInfo.bindings),
    references = backgroundScenarioInfo.references.plus(parsedScenarioInfo.references),
    ignoreFailure = ignoreFailure
)

fun setters(
    rest: String,
    backgroundSetters: Map<String, String>,
    scenarioSetters: Map<String, String>
): Map<String, String> {
    val parts = breakIntoPartsMaxLength(rest, 3)

    if (parts.size != 3 || parts[1] != "=")
        throw ContractException("Setter syntax is incorrect in \"$rest\". Syntax should be \"Then set <variable> = <selector>\"")

    val variableName = parts[0]
    val selector = parts[2]

    return backgroundSetters.plus(scenarioSetters).plus(variableName to selector)
}

fun values(
    rest: String,
    scenarioReferences: Map<String, References>,
    backgroundReferences: Map<String, References>,
    filePath: String
): Map<String, References> {
    val parts = breakIntoPartsMaxLength(rest, 3)

    if (parts.size != 3 || parts[1] != "from")
        throw ContractException("Incorrect syntax for value statement: $rest - it should be \"Given value <value name> from <$APPLICATION_NAME file name>\"")

    val valueStoreName = parts[0]
    val specFileName = parts[2]

    val specFilePath = ContractFileWithExports(specFileName, AnchorFile(filePath))

    return backgroundReferences.plus(scenarioReferences).plus(
        valueStoreName to References(
            valueStoreName,
            specFilePath,
            contractCache = contractCache
        )
    )
}

private val contractCache = ContractCache()

fun toFormDataPart(step: StepInfo, contractFilePath: String): MultiPartFormDataPattern {
    val parts = breakIntoPartsMaxLength(step.rest, 4)

    if (parts.size < 2)
        throw ContractException("There must be at least 2 words after request-part in $step.line")

    val (name, content) = parts.slice(0..1)

    return when {
        content.startsWith("@") -> {
            val contentType = parts.getOrNull(2)
            val contentEncoding = parts.getOrNull(3)

            val multipartFilename = content.removePrefix("@")

            val expandedFilenamePattern = when (val filenamePattern = parsedPattern(multipartFilename)) {
                is ExactValuePattern -> {
                    val multipartFilePath =
                        File(contractFilePath).absoluteFile.parentFile.resolve(multipartFilename).absolutePath
                    ExactValuePattern(StringValue(multipartFilePath))
                }
                else ->
                    filenamePattern
            }

            MultiPartFilePattern(name, expandedFilenamePattern, contentType, contentEncoding)
        }
        isPatternToken(content) -> {
            MultiPartContentPattern(name, parsedPattern(content))
        }
        else -> {
            MultiPartContentPattern(name, parsedPattern(content.trim()))
//            MultiPartContentPattern(name, ExactValuePattern(parsedValue(content)))
        }
    }
}

fun toPattern(step: StepInfo): Pattern {
    return when (val stringData = stringOrDocString(step.rest, step)) {
        "" -> {
            if (step.rowsList.isEmpty()) throw ContractException("Not enough information to describe a type in $step")
            rowsToTabularPattern(step.rowsList)
        }
        else -> parsedPattern(stringData)
    }
}

fun plusFormFields(
    formFields: Map<String, Pattern>,
    rest: String,
    rowsList: List<TableRow>
): Map<String, Pattern> =
    formFields.plus(when (rowsList.size) {
        0 -> toQueryParams(rest).map { (key, value) -> key to value }
        else -> rowsList.map { row -> row.cells[0].value to row.cells[1].value }
    }.associate { (key, value) -> key to parsedPattern(value) }
    )

private fun toQueryParams(rest: String) = rest.split("&")
    .map { breakIntoPartsMaxLength(it, 2) }

fun plusHeaderPattern(rest: String, headersPattern: HttpHeadersPattern): HttpHeadersPattern {
    val parts = breakIntoPartsMaxLength(rest, 2)

    return when (parts.size) {
        2 -> headersPattern.copy(pattern = headersPattern.pattern.plus(toPatternPair(parts[0], parts[1])))
        1 -> throw ContractException("Header $parts[0] should have a value")
        else -> throw ContractException("Unrecognised header params $rest")
    }
}

fun toPatternPair(key: String, value: String): Pair<String, Pattern> = key to parsedPattern(value)

fun breakIntoPartsMaxLength(whole: String, partCount: Int) = whole.split("\\s+".toRegex(), partCount)
fun breakIntoPartsMaxLength(whole: String, separator: String, partCount: Int) =
    whole.split(separator.toRegex(), partCount)

private val HTTP_METHODS = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")

fun parseGherkinString(gherkinData: String, sourceFilePath: String): GherkinDocument {
    return parseGherkinString(gherkinData)
        ?: throw ContractException("There was no contract in the file $sourceFilePath.")
}

internal fun parseGherkinString(gherkinData: String): GherkinDocument? {
    val idGenerator: IdGenerator = Incrementing()
    val parser = Parser(GherkinDocumentBuilder(idGenerator))
    return parser.parse(gherkinData)
}

internal fun lex(gherkinDocument: GherkinDocument, filePath: String = ""): Pair<String, List<Scenario>> =
    Pair(gherkinDocument.feature.name, lex(gherkinDocument.feature.children, filePath))

internal fun lex(featureChildren: List<FeatureChild>, filePath: String): List<Scenario> {
    return scenarioInfos(featureChildren, filePath)
        .map { scenarioInfo ->
            Scenario(
                scenarioInfo.scenarioName,
                scenarioInfo.httpRequestPattern,
                scenarioInfo.httpResponsePattern,
                scenarioInfo.expectedServerState,
                scenarioInfo.examples,
                scenarioInfo.patterns,
                scenarioInfo.fixtures,
                scenarioInfo.ignoreFailure,
                scenarioInfo.references,
                scenarioInfo.bindings,
                scenarioInfo.isGherkinScenario
            )
        }
}

fun scenarioInfos(
    featureChildren: List<FeatureChild>,
    filePath: String
): List<ScenarioInfo> {
    val openApiSpecification =
        toIncludedSpecification(featureChildren, { backgroundOpenApi(it) }) {
            OpenApiSpecification.fromFile(
                it,
                filePath
            )
        }

    val wsdlSpecification =
        toIncludedSpecification(featureChildren, { backgroundWsdl(it) }) { WsdlSpecification(WSDLFile(it)) }

    val includedSpecifications = listOfNotNull(openApiSpecification, wsdlSpecification)

    val scenarioInfosBelongingToIncludedSpecifications =
        includedSpecifications.map { it.toScenarioInfos().first }.flatten()

    val backgroundInfo = backgroundScenario(featureChildren)?.let { feature ->
        lexScenario(
            feature.background.steps
                .filter { !it.text.contains("openapi", true) }
                .filter { !it.text.contains("wsdl", true) },
            listOf(),
            emptyList(),
            null,
            filePath,
            includedSpecifications
        )
    } ?: ScenarioInfo()

    val specmaticScenarioInfos = scenarios(featureChildren).map { featureChild ->
        if (featureChild.scenario.name.isBlank() && openApiSpecification == null && wsdlSpecification == null)
            throw ContractException("Error at line ${featureChild.scenario.location.line}: scenario name must not be empty")

        val backgroundInfoCopy = backgroundInfo.copy(scenarioName = featureChild.scenario.name)

        lexScenario(
            featureChild.scenario.steps,
            featureChild.scenario.examples,
            featureChild.scenario.tags,
            backgroundInfoCopy,
            filePath,
            includedSpecifications
        )
    }

    return specmaticScenarioInfos.plus(scenarioInfosBelongingToIncludedSpecifications.filter { scenarioInfo ->
        specmaticScenarioInfos.none {
            it.httpResponsePattern.status == scenarioInfo.httpResponsePattern.status
                    && it.httpRequestPattern.matchesSignature(scenarioInfo.httpRequestPattern)
        }
    })
}

private fun toIncludedSpecification(
    featureChildren: List<FeatureChild>,
    selector: (List<FeatureChild>) -> Step?,
    creator: (String) -> IncludedSpecification
): IncludedSpecification? =
    selector(featureChildren)?.run { creator(text.split(" ")[1]) }

private fun backgroundScenario(featureChildren: List<FeatureChild>) =
    featureChildren.firstOrNull { it.background != null }

private fun backgroundOpenApi(featureChildren: List<FeatureChild>): Step? {
    return backgroundScenario(featureChildren)?.let { background ->
        background.background.steps.firstOrNull {
            it.keyword.contains("Given", true)
                    && it.text.contains("openapi", true)
        }
    }
}

private fun backgroundWsdl(featureChildren: List<FeatureChild>): Step? {
    return backgroundScenario(featureChildren)?.let { background ->
        background.background.steps.firstOrNull {
            it.keyword.contains("Given", true)
                    && it.text.contains("wsdl", true)
        }
    }
}

private fun scenarios(featureChildren: List<FeatureChild>) =
    featureChildren.filter { it.background == null }

fun toGherkinFeature(stub: NamedStub): String = toGherkinFeature("New Feature", listOf(stub))

private fun stubToClauses(namedStub: NamedStub): Pair<List<GherkinClause>, ExampleDeclarations> {
        val (requestClauses, typesFromRequest, examples) = toGherkinClauses(namedStub.stub.request)

        for (message in examples.messages) {
            logger.log(message)
        }

        val (responseClauses, allTypes, _) = toGherkinClauses(namedStub.stub.response, typesFromRequest)
        val typeClauses = toGherkinClauses(allTypes)
        return Pair(typeClauses.plus(requestClauses).plus(responseClauses), examples)
}

data class GherkinScenario(val scenarioName: String, val clauses: List<GherkinClause>)

fun toGherkinFeature(featureName: String, stubs: List<NamedStub>): String {
    val groupedStubs = stubs.map { stub ->
        val (clauses, examples) = stubToClauses(stub)
        val commentedExamples = addCommentsToExamples(examples, stub)

        Pair(GherkinScenario(stub.name, clauses), listOf(commentedExamples))
    }.fold(emptyMap<GherkinScenario, List<ExampleDeclarations>>()) { groups, (scenario, examples) ->
        groups.plus(scenario to groups.getOrDefault(scenario, emptyList()).plus(examples))
    }

    val scenarioStrings = groupedStubs.map { (nameAndClauses, examplesList) ->
        val (name, clauses) = nameAndClauses

        toGherkinScenario(name, clauses, examplesList)
    }

    return withFeatureClause(featureName, scenarioStrings.joinToString("\n\n"))
}

private fun addCommentsToExamples(examples: ExampleDeclarations, stub: NamedStub): ExampleDeclarations {
    val date = stub.stub.response.headers["Date"]
    return examples.withComment(date)
}

private fun List<String>.second(): String {
    return this[1]
}

fun similarURLPath(baseScenario: Scenario, newScenario: Scenario): Boolean {
    if(baseScenario.httpRequestPattern.httpPathPattern?.encompasses(newScenario.httpRequestPattern.httpPathPattern!!, baseScenario.resolver, newScenario.resolver) is Result.Success)
        return true

    val basePathParts = baseScenario.httpRequestPattern.httpPathPattern!!.pathSegmentPatterns
    val newPathParts = newScenario.httpRequestPattern.httpPathPattern!!.pathSegmentPatterns

    if(basePathParts.size != newPathParts.size)
        return false

    return basePathParts.zip(newPathParts).all { (base, new) ->
        isInteger(base) && isInteger(new) ||
                base.pattern.encompasses(new.pattern, baseScenario.resolver, newScenario.resolver) is Result.Success
    }
}

fun isInteger(
    base: URLPathSegmentPattern
) = base.pattern is ExactValuePattern && base.pattern.pattern.toStringLiteral().toIntOrNull() != null
