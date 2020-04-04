package run.qontract.core

import run.qontract.core.pattern.*
import run.qontract.core.utilities.mapZip
import run.qontract.core.value.StringValue
import run.qontract.core.value.True
import run.qontract.core.value.Value
import java.lang.StringBuilder

data class Scenario(val name: String, val httpRequestPattern: HttpRequestPattern, val httpResponsePattern: HttpResponsePattern, val expectedState: HashMap<String, Value>, val examples: List<PatternTable>, val patterns: HashMap<String, Pattern>, val fixtures: HashMap<String, Value>) {
    private fun serverStateMatches(actualState: HashMap<String, Value>, resolver: Resolver) =
            expectedState.keys == actualState.keys &&
                    mapZip(expectedState, actualState).all { (key, expectedStateValue, actualStateValue) ->
                        when {
                            actualStateValue == True || expectedStateValue == True -> true
                            expectedStateValue is StringValue && expectedStateValue.isPatternToken() -> {
                                val pattern = resolver.getPattern(expectedStateValue.string)
                                try { resolver.matchesPattern(key, pattern, pattern.parse(actualStateValue.toString(), resolver)).isTrue() } catch (e: Exception) { false }
                            }
                            else -> expectedStateValue.toStringValue() == actualStateValue.toStringValue()
                        }
                    }

    fun matches(httpRequest: HttpRequest, serverState: HashMap<String, Value>): Result {
        val resolver = Resolver(serverState, false).also {
            it.addCustomPatterns(patterns)
        }
        if (!serverStateMatches(serverState, resolver.makeCopy())) {
            return Result.Failure("Server State mismatch").also { it.updateScenario(this) }
        }
        return httpRequestPattern.matches(httpRequest, resolver).also {
            it.updateScenario(this)
        }
    }

    fun generateHttpResponse(actualServerState: HashMap<String, Value>): HttpResponse {
        val combinedState = Resolver(actualServerState, false).let { resolver ->
            resolver.customPatterns = patterns
            combineExpectedWithActual(expectedState, actualServerState, resolver)
        }

        return Resolver(combinedState, false).let { resolver ->
            resolver.customPatterns = patterns
            httpResponsePattern.generateResponse(resolver)
        }
    }

    private fun combineExpectedWithActual(
            expected: HashMap<String, Value>,
            actual: HashMap<String, Value>,
            resolver: Resolver
    ): HashMap<String, Value> {
        val combinedServerState = HashMap<String, Value>()

        for (key in expected.keys + actual.keys) {
            val expectedValue = expected.getValue(key)
            val actualValue = actual.getValue(key)

            when {
                key in expected && key in actual -> {
                    if(expectedValue == actualValue)
                        combinedServerState[key] = actualValue
                    else if(expectedValue is StringValue && expectedValue.isPatternToken()) {
                        val expectedPattern = resolver.getPattern(expectedValue.string)
                        try {
                            if(resolver.matchesPattern(key, expectedPattern, expectedPattern.parse(actualValue.toString(), resolver)).isTrue())
                                combinedServerState[key] = actualValue
                        } catch(e: Throwable) {
                            throw ContractParseException("Couldn't match state values. Expected $expectedValue in key $key, actual value is $actualValue")
                        }
                    }
                }
                key in expected -> combinedServerState[key] = expectedValue
                key in actual -> combinedServerState[key] = actualValue
            }
        }

        return combinedServerState
    }

    fun generateHttpRequest(): HttpRequest {
        val resolver = Resolver(expectedState, false)
        resolver.customPatterns = patterns
        return httpRequestPattern.generate(resolver)
    }

    fun matches(httpResponse: HttpResponse): Result {
        val resolver = Resolver(expectedState, false)
        resolver.customPatterns = patterns
        return try {
            httpResponsePattern.matches(httpResponse, resolver).also { it.updateScenario(this) }.let {
                when (it) {
                    is Result.Failure -> it.add("Response did not match")
                            .also { failure -> failure.updateScenario(this) }
                    else -> it
                }
            }
        } catch (exception: Throwable) {
            return Result.Failure("Error: ${exception.message}")
        }
    }

    private fun newBasedOn(row: Row): List<Scenario> {
        val resolver = Resolver(expectedState, false)
        resolver.customPatterns = patterns

        val newExpectedServerState = newExpectedServerStateBasedOn(row, expectedState, resolver)
        return httpRequestPattern.newBasedOn(row, resolver).map { newHttpRequestPattern ->
            Scenario(name, newHttpRequestPattern, httpResponsePattern, newExpectedServerState, examples, patterns, fixtures)
        }
    }

    fun generateTestScenarios(): List<Scenario> =
            when (examples.size) {
                0 -> listOf(Row())
                else -> examples.flatMap { it.rows }
            }.flatMap { row -> newBasedOn(row) }

    private fun newExpectedServerStateBasedOn(row: Row, expectedServerState: Map<String, Value>, resolver: Resolver): HashMap<String, Value> {
        return HashMap(expectedServerState.mapValues { (key, value) ->
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
        })
    }

    val serverState: Map<String, Value>
        get() = expectedState

    fun matchesMock(response: HttpResponse): Result {
        val resolver = Resolver(IgnoreFacts(), true)
        resolver.customPatterns = patterns
        return httpResponsePattern.matchesMock(response, resolver).also {
            it.updateScenario(this)
        }
    }

    fun generateHttpResponseFrom(response: HttpResponse?): HttpResponse {
        val resolver = Resolver(expectedState, false)
        resolver.customPatterns = patterns
        return HttpResponsePattern(response!!).generateResponse(resolver)
    }

    override fun toString(): String {
        val scenarioDescription = StringBuilder()
        scenarioDescription.append("Scenario: ")
        when {
            !name.isNullOrEmpty() -> scenarioDescription.append("$name ")
        }
        return scenarioDescription.append("$httpRequestPattern").toString()
    }

    fun newBasedOn(scenario: Scenario): Scenario =
        Scenario(this.name, this.httpRequestPattern, this.httpResponsePattern, this.expectedState, scenario.examples, this.patterns, this.fixtures)

    fun newBasedOn(suggestions: List<Scenario>) =
        this.newBasedOn(suggestions.find { it.name == this.name } ?: this)

}
