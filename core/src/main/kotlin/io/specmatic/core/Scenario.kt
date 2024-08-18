package io.specmatic.core

import io.specmatic.conversions.OpenApiSpecification
import io.specmatic.core.log.logger
import io.specmatic.core.pattern.*
import io.specmatic.core.utilities.capitalizeFirstChar
import io.specmatic.core.utilities.mapZip
import io.specmatic.core.utilities.nullOrExceptionString
import io.specmatic.core.value.*
import io.specmatic.mock.ScenarioStub
import io.specmatic.stub.RequestContext

object ContractAndStubMismatchMessages : MismatchMessages {
    override fun mismatchMessage(expected: String, actual: String): String {
        return "Contract expected $expected but stub contained $actual"
    }

    override fun unexpectedKey(keyLabel: String, keyName: String): String {
        return "${keyLabel.lowercase().capitalizeFirstChar()} named $keyName in the stub was not in the contract"
    }

    override fun expectedKeyWasMissing(keyLabel: String, keyName: String): String {
        return "${keyLabel.lowercase().capitalizeFirstChar()} named $keyName in the contract was not found in the stub"
    }
}

interface ScenarioDetailsForResult {
    val status: Int
    val ignoreFailure: Boolean
    val name: String
    val method: String
    val path: String

    fun testDescription(): String
}

data class Scenario(
    override val name: String,
    val httpRequestPattern: HttpRequestPattern,
    val httpResponsePattern: HttpResponsePattern,
    val expectedFacts: Map<String, Value>,
    val examples: List<Examples>,
    val patterns: Map<String, Pattern>,
    val fixtures: Map<String, Value>,
    override val ignoreFailure: Boolean = false,
    val references: Map<String, References> = emptyMap(),
    val bindings: Map<String, String> = emptyMap(),
    val isGherkinScenario: Boolean = false,
    val isNegative: Boolean = false,
    val badRequestOrDefault: BadRequestOrDefault? = null,
    val exampleName: String? = null,
    val generatedFromExamples: Boolean = examples.isNotEmpty(),
    val sourceProvider:String? = null,
    val sourceRepository:String? = null,
    val sourceRepositoryBranch:String? = null,
    val specification:String? = null,
    val serviceType:String? = null,
    val generativePrefix: String = "",
    val statusInDescription: String = httpResponsePattern.status.toString(),
    val disambiguate: () -> String = { "" },
    val descriptionFromPlugin: String? = null
): ScenarioDetailsForResult {
    constructor(scenarioInfo: ScenarioInfo) : this(
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
        sourceProvider = scenarioInfo.sourceProvider,
        sourceRepository = scenarioInfo.sourceRepository,
        sourceRepositoryBranch = scenarioInfo.sourceRepositoryBranch,
        specification = scenarioInfo.specification,
        serviceType = scenarioInfo.serviceType
    )

    val apiIdentifier: String
        get() = "$method $path $status"

    override val method: String
        get() {
            return httpRequestPattern.method ?: ""
        }

    override val path: String
        get() {
            return httpRequestPattern.httpPathPattern?.path ?: ""
        }

    override val status: Int
        get() {
            return if(isNegative) 400 else httpResponsePattern.status
        }

    private fun serverStateMatches(actualState: Map<String, Value>, resolver: Resolver) =
        expectedFacts.keys == actualState.keys &&
                mapZip(expectedFacts, actualState).all { (key, expectedStateValue, actualStateValue) ->
                    when {
                        actualStateValue == True || expectedStateValue == True -> true
                        expectedStateValue is StringValue && expectedStateValue.isPatternToken() -> {
                            val pattern = resolver.getPattern(expectedStateValue.string)
                            try {
                                resolver.matchesPattern(
                                    key,
                                    pattern,
                                    pattern.parse(actualStateValue.toString(), resolver)
                                ).isSuccess()
                            } catch (e: Exception) {
                                false
                            }
                        }
                        else -> expectedStateValue.toStringLiteral() == actualStateValue.toStringLiteral()
                    }
                }

    fun matches(
        httpRequest: HttpRequest,
        serverState: Map<String, Value>,
        mismatchMessages: MismatchMessages = DefaultMismatchMessages,
        unexpectedKeyCheck: UnexpectedKeyCheck? = null
    ): Result {
        val resolver = Resolver(serverState, false, patterns).copy(mismatchMessages = mismatchMessages).let {
            if(unexpectedKeyCheck != null) {
                val keyCheck = it.findKeyErrorCheck
                it.copy(findKeyErrorCheck = keyCheck.copy(unexpectedKeyCheck = unexpectedKeyCheck))
            }
            else
                it
        }
        return matches(httpRequest, serverState, resolver, resolver)
    }

    fun matchesStub(
        httpRequest: HttpRequest,
        serverState: Map<String, Value>,
        mismatchMessages: MismatchMessages = DefaultMismatchMessages
    ): Result {
        val headersResolver = Resolver(serverState, false, patterns).copy(mismatchMessages = mismatchMessages)
        val nonHeadersResolver = headersResolver.disableOverrideUnexpectedKeycheck()

        return matches(httpRequest, serverState, nonHeadersResolver, headersResolver)
    }

    private fun matches(
        httpRequest: HttpRequest,
        serverState: Map<String, Value>,
        resolver: Resolver,
        headersResolver: Resolver
    ): Result {
        if (!serverStateMatches(serverState, resolver)) {
            return Result.Failure("Facts mismatch", breadCrumb = "FACTS").also { it.updateScenario(this) }
        }
        return httpRequestPattern.matches(httpRequest, resolver, headersResolver).also {
            it.updateScenario(this)
        }
    }

    fun generateHttpResponse(actualFacts: Map<String, Value>, requestContext: Context = NoContext): HttpResponse =
        scenarioBreadCrumb(this) {
            Resolver(emptyMap(), false, patterns)
            val resolver = Resolver(actualFacts, false, patterns)
            val facts = combineFacts(expectedFacts, actualFacts, resolver)

            httpResponsePattern.generateResponse(resolver.copy(factStore = CheckFacts(facts), context = requestContext))
        }

    private fun combineFacts(
        expected: Map<String, Value>,
        actual: Map<String, Value>,
        resolver: Resolver
    ): Map<String, Value> {
        val combinedServerState = HashMap<String, Value>()

        for (key in expected.keys + actual.keys) {
            val expectedValue = expected.getValue(key)
            val actualValue = actual.getValue(key)

            when {
                key in expected && key in actual -> {
                    when {
                        expectedValue == actualValue -> combinedServerState[key] = actualValue
                        expectedValue is StringValue && expectedValue.isPatternToken() -> {
                            ifMatches(key, expectedValue, actualValue, resolver) {
                                combinedServerState[key] = actualValue
                            }
                        }
                    }
                }
                key in expected -> combinedServerState[key] = expectedValue
                key in actual -> combinedServerState[key] = actualValue
            }
        }

        return combinedServerState
    }

    private fun ifMatches(
        key: String,
        expectedValue: StringValue,
        actualValue: Value,
        resolver: Resolver,
        code: () -> Unit
    ) {
        val expectedPattern = resolver.getPattern(expectedValue.string)

        try {
            if (resolver.matchesPattern(key, expectedPattern, expectedPattern.parse(actualValue.toString(), resolver))
                    .isSuccess()
            )
                code()
        } catch (e: Throwable) {
            throw ContractException("Couldn't match state values. Expected $expectedValue in key $key" +
                ", actual value is $actualValue", exceptionCause = e)
        }
    }

    fun generateHttpRequest(flagsBased: FlagsBased = DefaultStrategies): HttpRequest =
        scenarioBreadCrumb(this) { httpRequestPattern.generate(flagsBased.update(Resolver(expectedFacts, false, patterns))) }

    fun matches(httpRequest: HttpRequest, httpResponse: HttpResponse, mismatchMessages: MismatchMessages = DefaultMismatchMessages, unexpectedKeyCheck: UnexpectedKeyCheck? = null): Result {
        val resolver = updatedResolver(mismatchMessages, unexpectedKeyCheck).copy(context = RequestContext(httpRequest))

        return matches(httpResponse, mismatchMessages, unexpectedKeyCheck, resolver)
    }

    fun matches(httpResponse: HttpResponse, mismatchMessages: MismatchMessages = DefaultMismatchMessages, unexpectedKeyCheck: UnexpectedKeyCheck? = null): Result {
        val resolver = updatedResolver(mismatchMessages, unexpectedKeyCheck)

        return matches(httpResponse, mismatchMessages, unexpectedKeyCheck, resolver)
    }

    private fun updatedResolver(
        mismatchMessages: MismatchMessages,
        unexpectedKeyCheck: UnexpectedKeyCheck?
    ): Resolver {
        return Resolver(expectedFacts, false, patterns).copy(mismatchMessages = mismatchMessages).let {
            if (unexpectedKeyCheck != null)
                it.copy(findKeyErrorCheck = it.findKeyErrorCheck.copy(unexpectedKeyCheck = unexpectedKeyCheck))
            else
                it
        }
    }

    fun matches(httpResponse: HttpResponse, mismatchMessages: MismatchMessages = DefaultMismatchMessages, unexpectedKeyCheck: UnexpectedKeyCheck? = null, resolver: Resolver): Result {

        if (this.isNegative) {
            return if (is4xxResponse(httpResponse)) {
                if(badRequestOrDefault != null && badRequestOrDefault.supports(httpResponse.status))
                    badRequestOrDefault.matches(httpResponse, resolver).updateScenario(this)
                else
                    Result.Failure("Received ${httpResponse.status}, but the specification does not contain a 4xx response, hence unable to verify this response", breadCrumb = "RESPONSE.STATUS").updateScenario(this)
            }
            else
                Result.Failure("Expected 4xx status, but received ${httpResponse.status}", breadCrumb = "RESPONSE.STATUS").updateScenario(this)
        }

        return try {
            httpResponsePattern.matches(httpResponse, resolver).updateScenario(this)
        } catch (exception: Throwable) {
            Result.Failure("Exception: ${exception.message}")
        }
    }

    private fun is4xxResponse(httpResponse: HttpResponse) = (400..499).contains(httpResponse.status)

    object ContractAndRowValueMismatch : MismatchMessages {
        override fun mismatchMessage(expected: String, actual: String): String {
            return "Contract expected $expected but found value $actual"
        }

        override fun unexpectedKey(keyLabel: String, keyName: String): String {
            return "${
                keyLabel.lowercase().capitalizeFirstChar()
            } named $keyName in the example was not in the specification"
        }

        override fun expectedKeyWasMissing(keyLabel: String, keyName: String): String {
            return "${
                keyLabel.lowercase().capitalizeFirstChar()
            } named $keyName in the specification was not found in the example"
        }
    }

    private fun newBasedOn(row: Row, flagsBased: FlagsBased): Sequence<ReturnValue<Scenario>> {
        val ignoreFailure = this.ignoreFailure || row.name.startsWith("[WIP]")
        val resolver =
            Resolver(expectedFacts, false, patterns)
            .copy(
                mismatchMessages = ContractAndRowValueMismatch
            ).let { flagsBased.update(it) }

        val newExpectedServerState = newExpectedServerStateBasedOn(row, expectedFacts, fixtures, resolver)

        return scenarioBreadCrumb(this) {
            attempt {
                val newResponsePattern: HttpResponsePattern = this.httpResponsePattern.withResponseExampleValue(row, resolver)

                val (newRequestPatterns: Sequence<ReturnValue<HttpRequestPattern>>, generativePrefix: String) = when (isNegative) {
                    false -> Pair(httpRequestPattern.newBasedOn(row, resolver, httpResponsePattern.status), flagsBased.positivePrefix)
                    else -> Pair(httpRequestPattern.negativeBasedOn(row, resolver.copy(isNegative = true)), flagsBased.negativePrefix)
                }

                newRequestPatterns.map { newHttpRequestPattern ->
                    newHttpRequestPattern.ifValue {
                        this.copy(
                            httpRequestPattern = it,
                            httpResponsePattern = newResponsePattern,
                            expectedFacts = newExpectedServerState,
                            ignoreFailure = ignoreFailure,
                            exampleName = row.name,
                            generativePrefix = generativePrefix,
                        )
                    }
                }
            }
        }
    }

    private fun newBasedOnBackwardCompatibility(row: Row): Sequence<Scenario> {
        val resolver = Resolver(expectedFacts, false, patterns)

        val newExpectedServerState = newExpectedServerStateBasedOn(row, expectedFacts, fixtures, resolver)

        return httpRequestPattern.newBasedOn(resolver).map { newHttpRequestPattern ->
            this.copy(
                httpRequestPattern = newHttpRequestPattern,
                expectedFacts = newExpectedServerState
            )
        }
    }

    fun validExamplesOrException(
        flagsBased: FlagsBased,
    ) {
        val rowsToValidate = examples.flatMap { it.rows }

        val updatedResolver = flagsBased.update(resolver)

        val errors = rowsToValidate.map { row ->
            val resolverForExample = resolverForValidation(updatedResolver, row)

            val requestError = nullOrExceptionString {
                validateRequestExample(row, resolverForExample)
            }

            val responseError = nullOrExceptionString {
                validateResponseExample(row, resolverForExample)
            }

            val errors = listOf(requestError, responseError).filterNotNull().map { it.prependIndent("  ") }

            if(errors.isNotEmpty()) {
                val title = if(row.fileSource != null) {
                    "Error loading example for ${this.apiDescription.trim()} from ${row.fileSource}"
                } else {
                    "Error loading example named ${row.name} for ${this.apiDescription.trim()}"
                }

                listOf(title).plus(errors).joinToString("${System.lineSeparator()}${System.lineSeparator()}").also { message ->
                    logger.log(message)

                    logger.newLine()
                }
            } else
                null
        }.filterNotNull()

        if(errors.isNotEmpty())
            throw ContractException(errors.joinToString("${System.lineSeparator()}${System.lineSeparator()}"))
    }

    private fun resolverForValidation(
        updatedResolver: Resolver,
        row: Row
    ) = updatedResolver.copy(
        mismatchMessages = object : MismatchMessages {
            override fun mismatchMessage(expected: String, actual: String): String {
                return "Expected $expected as per the specification, but the example ${row.name} had $actual."
            }

            override fun unexpectedKey(keyLabel: String, keyName: String): String {
                return "The $keyLabel $keyName was found in the example ${row.name} but was not in the specification."
            }

            override fun expectedKeyWasMissing(keyLabel: String, keyName: String): String {
                return "The $keyLabel $keyName in the specification was missing in example ${row.name}"
            }
        },
        mockMode = true
    )

    private fun validateResponseExample(row: Row, resolverForExample: Resolver): Result {
        val responseExample: HttpResponse? = row.responseExample

        if (responseExample != null) {
            val responseMatchResult =
                httpResponsePattern.matches(responseExample, resolverForExample)

            return responseMatchResult
        }

        return Result.Success()
    }

    private fun validateRequestExample(row: Row, resolverForExample: Resolver): Result {
        if(row.requestExample != null) {
            val result = httpRequestPattern.matches(row.requestExample, resolverForExample, resolverForExample)
            if(result is Result.Failure && !status.toString().startsWith("4"))
                return result
        } else {
            httpRequestPattern.newBasedOn(row, resolverForExample, status).first().value
        }

        return Result.Success()
    }

    fun generateTestScenarios(
        flagsBased: FlagsBased,
        variables: Map<String, String> = emptyMap(),
        testBaseURLs: Map<String, String> = emptyMap(),
    ): Sequence<ReturnValue<Scenario>> {
        val referencesWithBaseURLs = references.mapValues { (_, reference) ->
            reference.copy(variables = variables, baseURLs = testBaseURLs)
        }

        return scenarioBreadCrumb(this) {
            when (examples.size) {
                0 -> sequenceOf(Row())
                else -> examples.asSequence().flatMap {
                    it.rows.map { row ->
                        row.copy(variables = variables, references = referencesWithBaseURLs)
                    }
                }
            }.flatMap { row ->
                newBasedOn(row, flagsBased)
            }
        }
    }

    fun generateBackwardCompatibilityScenarios(
        variables: Map<String, String> = emptyMap(),
        testBaseURLs: Map<String, String> = emptyMap()
    ): List<Scenario> {
        val referencesWithBaseURLs = references.mapValues { (_, reference) ->
            reference.copy(variables = variables, baseURLs = testBaseURLs)
        }

        return scenarioBreadCrumb(this) {
            when (examples.size) {
                0 -> listOf(Row())
                else -> examples.flatMap {
                    it.rows.map { row ->
                        row.copy(variables = variables, references = referencesWithBaseURLs)
                    }
                }
            }.flatMap { row ->
                newBasedOnBackwardCompatibility(row)
            }
        }
    }

    val resolver: Resolver = Resolver(newPatterns = patterns)

    val serverState: Map<String, Value>
        get() = expectedFacts

    fun matchesMock(
        request: HttpRequest,
        response: HttpResponse,
        mismatchMessages: MismatchMessages = DefaultMismatchMessages
    ): Result {
        scenarioBreadCrumb(this) {
            val resolver = Resolver(
                IgnoreFacts(),
                true,
                patterns,
                findKeyErrorCheck = DefaultKeyCheck.disableOverrideUnexpectedKeycheck(),
                mismatchMessages = mismatchMessages
            )

            val requestMatchResult = attempt(breadCrumb = "REQUEST") { httpRequestPattern.matches(request, resolver) }

            if (requestMatchResult is Result.Failure)
                requestMatchResult.updateScenario(this)

            if (requestMatchResult is Result.Failure && response.status != httpResponsePattern.status)
                return Result.Failure(
                    cause = requestMatchResult,
                    failureReason = FailureReason.RequestMismatchButStatusAlsoWrong
                )

            val responseMatchResult =
                attempt(breadCrumb = "RESPONSE") { httpResponsePattern.matchesMock(response, resolver) }

            if (requestMatchResult is Result.Failure)
                responseMatchResult.updateScenario(this)

            val failures = listOf(requestMatchResult, responseMatchResult).filterIsInstance<Result.Failure>()

            return if (failures.isEmpty())
                Result.Success()
            else
                Result.Failure.fromFailures(failures)
        }
    }

    fun resolverAndResponseForExpectation(response: HttpResponse): Pair<Resolver, HttpResponse> =
        scenarioBreadCrumb(this) {
            attempt(breadCrumb = "RESPONSE") {
                val resolver = Resolver(expectedFacts, false, patterns)
                Pair(resolver, httpResponsePattern.fromResponseExpectation(response).generateResponse(resolver))
            }
        }

    val apiDescription: String = "$method $path ${disambiguate()}-> $statusInDescription"

    override fun testDescription(): String {
        val exampleIdentifier = if(exampleName.isNullOrBlank()) "" else { " | EX:${exampleName.trim()}" }

        val generativePrefix = this.generativePrefix

        val apiDescription = descriptionFromPlugin ?: apiDescription
        return "$generativePrefix Scenario: $apiDescription$exampleIdentifier"
    }

    fun newBasedOn(scenario: Scenario): Scenario {
        return this.copy(
            examples = scenario.examples,
            references = scenario.references
        )
    }

    fun newBasedOn(suggestions: List<Scenario>) =
        this.newBasedOn(suggestions.find { it.name == this.name } ?: this)

    fun isA2xxScenario(): Boolean = this.httpResponsePattern.status in 200..299
    fun negativeBasedOn(badRequestOrDefault: BadRequestOrDefault?): Scenario {
        return this.copy(
            isNegative = true,
            badRequestOrDefault = badRequestOrDefault,
            statusInDescription = "4xx",
            generativePrefix = "-ve",
        )
    }

    fun getStatus(response: HttpResponse?): Int {
        // TODO: This should return a string so that we can return a 4xx when response is null for a negative scenario
        return when {
            response == null -> status
            isNegative -> response.status
            else -> status
        }
    }

    fun useExamples(externalisedJSONExamples: Map<OpenApiSpecification.OperationIdentifier, List<Row>>): Scenario {
        val matchingTestData: Map<OpenApiSpecification.OperationIdentifier, List<Row>> = matchingRows(externalisedJSONExamples)

        val newExamples: List<Examples> = matchingTestData.map { (operationId, rows) ->
            if(rows.isEmpty())
                return@map emptyList()

            val rowsWithPathData: List<Row> = rows.map { row -> httpRequestPattern.addPathParamsToRows(operationId.requestPath, row, resolver) }

            val columns = rowsWithPathData.first().columnNames

            listOf(Examples(columns, rowsWithPathData))
        }.flatten()

        return this.copy(examples = this.examples + newExamples)
    }

    private fun matchingRows(externalisedJSONExamples: Map<OpenApiSpecification.OperationIdentifier, List<Row>>): Map<OpenApiSpecification.OperationIdentifier, List<Row>> {
        val patternMatchingResolver = resolver.copy(mockMode = true)

        return externalisedJSONExamples.filter { (operationId, rows) ->
            operationId.requestMethod.equals(method, ignoreCase = true)
                    && operationId.responseStatus == status
                    && httpRequestPattern.matchesPath(operationId.requestPath, patternMatchingResolver).isSuccess()
        }
    }

    fun resolveSubtitutions(request: HttpRequest, response: HttpResponse): HttpResponse {
        val substitution = httpRequestPattern.getSubstitution(request, resolver)
        return httpResponsePattern.resolveSubstitutions(substitution, response)
    }

    fun matchesTemplate(template: ScenarioStub): Boolean {
        val requestResult = httpRequestPattern.httpPathPattern?.matches(template.request.path!!, resolver)
        return requestResult is Result.Success && (template.response.status == httpResponsePattern.status || httpResponsePattern.status == DEFAULT_RESPONSE_CODE)
    }
}

fun newExpectedServerStateBasedOn(
    row: Row,
    expectedServerState: Map<String, Value>,
    fixtures: Map<String, Value>,
    resolver: Resolver
): Map<String, Value> =
    attempt(errorMessage = "Scenario fact generation failed") {
        expectedServerState.mapValues { (key, value) ->
            when {
                row.containsField(key) -> {
                    val fieldValue = row.getField(key)

                    when {
                        fixtures.containsKey(fieldValue) -> fixtures.getValue(fieldValue)
                        isPatternToken(fieldValue) -> {
                            val fieldPattern = resolver.getPattern(fieldValue)
                            resolver.withCyclePrevention(fieldPattern, fieldPattern::generate)
                        }
                        else -> StringValue(fieldValue)
                    }
                }
                value is StringValue && isPatternToken(value) -> resolver.getPattern(value.string).generate(resolver)
                else -> value
            }
        }
    }

object ContractAndResponseMismatch : MismatchMessages {
    override fun mismatchMessage(expected: String, actual: String): String {
        return "Contract expected $expected but response contained $actual"
    }

    override fun unexpectedKey(keyLabel: String, keyName: String): String {
        return "${keyLabel.lowercase().capitalizeFirstChar()} named $keyName in the response was not in the specification"
    }

    override fun expectedKeyWasMissing(keyLabel: String, keyName: String): String {
        return "${
            keyLabel.lowercase().capitalizeFirstChar()
        } named $keyName in the specification was not found in the response"
    }
}
