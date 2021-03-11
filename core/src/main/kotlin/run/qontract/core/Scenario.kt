package run.qontract.core

import run.qontract.core.pattern.*
import run.qontract.core.utilities.exceptionCauseMessage
import run.qontract.core.utilities.mapZip
import run.qontract.core.value.KafkaMessage
import run.qontract.core.value.StringValue
import run.qontract.core.value.True
import run.qontract.core.value.Value
import run.qontract.test.TestExecutor

data class Scenario(
    val name: String,
    val httpRequestPattern: HttpRequestPattern,
    val httpResponsePattern: HttpResponsePattern,
    val expectedFacts: Map<String, Value>,
    val examples: List<Examples>,
    val patterns: Map<String, Pattern>,
    val fixtures: Map<String, Value>,
    val kafkaMessagePattern: KafkaMessagePattern? = null,
    val ignoreFailure: Boolean = false,
    val references: Map<String, References> = emptyMap(),
    val setters: Map<String, String> = emptyMap()
) {
    private fun serverStateMatches(actualState: Map<String, Value>, resolver: Resolver) =
            expectedFacts.keys == actualState.keys &&
                    mapZip(expectedFacts, actualState).all { (key, expectedStateValue, actualStateValue) ->
                        when {
                            actualStateValue == True || expectedStateValue == True -> true
                            expectedStateValue is StringValue && expectedStateValue.isPatternToken() -> {
                                val pattern = resolver.getPattern(expectedStateValue.string)
                                try { resolver.matchesPattern(key, pattern, pattern.parse(actualStateValue.toString(), resolver)).isTrue() } catch (e: Exception) { false }
                            }
                            else -> expectedStateValue.toStringValue() == actualStateValue.toStringValue()
                        }
                    }

    fun matches(httpRequest: HttpRequest, serverState: Map<String, Value>): Result {
        val resolver = Resolver(serverState, false, patterns)
        return matches(httpRequest, serverState, resolver, resolver)
    }

    fun matchesStub(httpRequest: HttpRequest, serverState: Map<String, Value>): Result {
        val headersResolver = Resolver(serverState, false, patterns)
        val nonHeadersResolver = headersResolver.copy(findMissingKey = checkAllKeys)

        return matches(httpRequest, serverState, nonHeadersResolver, headersResolver)
    }

    private fun matches(httpRequest: HttpRequest, serverState: Map<String, Value>, resolver: Resolver, headersResolver: Resolver): Result {
        if (!serverStateMatches(serverState, resolver)) {
            return Result.Failure("Facts mismatch", breadCrumb = "FACTS").also { it.updateScenario(this) }
        }
        return httpRequestPattern.matches(httpRequest, resolver, headersResolver).also {
            it.updateScenario(this)
        }
    }

    fun generateHttpResponse(actualFacts: Map<String, Value>): HttpResponse =
        scenarioBreadCrumb(this) {
            Resolver(emptyMap(), false, patterns)
            val resolver = Resolver(actualFacts, false, patterns)
            val facts = combineFacts(expectedFacts, actualFacts, resolver)

            httpResponsePattern.generateResponse(resolver.copy(factStore = CheckFacts(facts)))
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

    private fun ifMatches(key: String, expectedValue: StringValue, actualValue: Value, resolver: Resolver, code: () -> Unit) {
        val expectedPattern = resolver.getPattern(expectedValue.string)

        try {
            if(resolver.matchesPattern(key, expectedPattern, expectedPattern.parse(actualValue.toString(), resolver)).isTrue())
                code()
        } catch(e: Throwable) {
            throw ContractException("Couldn't match state values. Expected $expectedValue in key $key, actual value is $actualValue")
        }
    }

    fun generateHttpRequest(): HttpRequest =
            scenarioBreadCrumb(this) { httpRequestPattern.generate(Resolver(expectedFacts, false, patterns)) }

    fun matches(httpResponse: HttpResponse): Result {
        val resolver = Resolver(expectedFacts, false, patterns)

        return try {
            httpResponsePattern.matches(httpResponse, resolver).updateScenario(this)
        } catch (exception: Throwable) {
            return Result.Failure("Exception: ${exception.message}")
        }
    }

    private fun newBasedOn(row: Row): List<Scenario> {
        val resolver = Resolver(expectedFacts, false, patterns)

        val newExpectedServerState = newExpectedServerStateBasedOn(row, expectedFacts, fixtures, resolver)

        return when (kafkaMessagePattern) {
            null -> httpRequestPattern.newBasedOn(row, resolver).map { newHttpRequestPattern ->
                Scenario(
                    name,
                    newHttpRequestPattern,
                    httpResponsePattern,
                    newExpectedServerState,
                    examples,
                    patterns,
                    fixtures,
                    kafkaMessagePattern,
                    ignoreFailure,
                    references,
                    setters
                )
            }
            else -> {
                kafkaMessagePattern.newBasedOn(row, resolver).map { newKafkaMessagePattern ->
                    Scenario(
                        name,
                        httpRequestPattern,
                        httpResponsePattern,
                        newExpectedServerState,
                        examples,
                        patterns,
                        fixtures,
                        newKafkaMessagePattern,
                        ignoreFailure,
                        references,
                        setters
                    )
                }
            }
        }
    }

    fun generateTestScenarios(context: Map<String, String> = emptyMap(), testBaseURLs: Map<String, String> = emptyMap()): List<Scenario> {
        val referencesWithBaseURLs = references.mapValues { (key, reference) ->
            val qontractFileName = reference.qontractFileName
            val baseURL = testBaseURLs[qontractFileName]
                ?: throw ContractException("Base url for qontract file $qontractFileName was not supplied.")

            reference.copy(baseURL = baseURL)
        }

        return scenarioBreadCrumb(this) {
            when (examples.size) {
                0 -> listOf(Row())
                else -> examples.flatMap {
                    it.rows.map { row ->
                        row.copy(variables = context, references = referencesWithBaseURLs)
                    }
                }
            }.flatMap { row ->
                newBasedOn(row)
            }
        }
    }

    val resolver: Resolver = Resolver(newPatterns = patterns)

    val serverState: Map<String, Value>
        get() = expectedFacts

    fun matchesMock(request: HttpRequest, response: HttpResponse): Result {
        return scenarioBreadCrumb(this) {
            val resolver = Resolver(IgnoreFacts(), true, patterns, findMissingKey = checkAllKeys)

            when (val requestMatchResult = attempt(breadCrumb = "REQUEST") { httpRequestPattern.matches(request, resolver) }) {
                is Result.Failure -> requestMatchResult.updateScenario(this)
                else ->
                    when (val responseMatchResult = attempt(breadCrumb = "RESPONSE") { httpResponsePattern.matchesMock(response, resolver) }) {
                        is Result.Failure -> {
                            responseMatchResult.updateScenario(this)
                        }
                        else -> responseMatchResult
                    }
            }
        }
    }

    fun matchesMock(kafkaMessage: KafkaMessage): Result {
        return kafkaMessagePattern?.matches(kafkaMessage, resolver.copy(findMissingKey = checkAllKeys)) ?: Result.Failure("This scenario does not have a Kafka mock")
    }

    fun resolverAndResponseFrom(response: HttpResponse?): Pair<Resolver, HttpResponse> =
        scenarioBreadCrumb(this) {
            attempt(breadCrumb = "RESPONSE") {
                val resolver = Resolver(expectedFacts, false, patterns)
                Pair(resolver, HttpResponsePattern(response!!).generateResponse(resolver))
            }
        }

    override fun toString(): String {
        val scenarioDescription = StringBuilder()
        scenarioDescription.append("Scenario: ")
        when {
            name.isNotEmpty() -> scenarioDescription.append("$name ")
        }

        return if(kafkaMessagePattern != null)
            scenarioDescription.append(kafkaMessagePattern.topic).toString()
        else
            scenarioDescription.append("$httpRequestPattern").toString()
    }

    fun newBasedOn(scenario: Scenario): Scenario =
        Scenario(
            this.name,
            this.httpRequestPattern,
            this.httpResponsePattern,
            this.expectedFacts,
            scenario.examples,
            this.patterns,
            this.fixtures,
            this.kafkaMessagePattern,
            this.ignoreFailure,
            scenario.references,
            setters
        )

    fun newBasedOn(suggestions: List<Scenario>) =
        this.newBasedOn(suggestions.find { it.name == this.name } ?: this)
}

fun newExpectedServerStateBasedOn(row: Row, expectedServerState: Map<String, Value>, fixtures: Map<String, Value>, resolver: Resolver): Map<String, Value> =
        attempt(errorMessage = "Scenario fact generation failed") {
            expectedServerState.mapValues { (key, value) ->
                when {
                    row.containsField(key) -> {
                        val fieldValue = row.getField(key)

                        when {
                            fixtures.containsKey(fieldValue) -> fixtures.getValue(fieldValue)
                            isPatternToken(fieldValue) -> resolver.getPattern(fieldValue).generate(resolver)
                            else -> StringValue(fieldValue)
                        }
                    }
                    value is StringValue && isPatternToken(value) -> resolver.getPattern(value.string).generate(resolver)
                    else -> value
                }
            }
        }

fun executeTest(testScenario: Scenario, testExecutor: TestExecutor): Result {
    val request = testScenario.generateHttpRequest()

    return try {
        testExecutor.setServerState(testScenario.serverState)

        val response = testExecutor.execute(request)

        when (response.headers.getOrDefault(QONTRACT_RESULT_HEADER, "success")) {
            "failure" -> Result.Failure(response.body.toStringValue()).updateScenario(testScenario)
            else -> testScenario.matches(response)
        }.withDefinedVariablesSet(testScenario.setters, response)
    } catch (exception: Throwable) {
        Result.Failure(exceptionCauseMessage(exception))
                .also { failure -> failure.updateScenario(testScenario) }
    }
}
