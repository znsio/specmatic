package run.qontract.core

import run.qontract.core.pattern.*
import run.qontract.core.utilities.mapZip
import run.qontract.core.value.StringValue
import run.qontract.core.value.True
import run.qontract.core.value.Value
import java.lang.StringBuilder

data class Scenario(val name: String, val httpRequestPattern: HttpRequestPattern, val httpResponsePattern: HttpResponsePattern, val expectedFacts: HashMap<String, Value>, val examples: List<Examples>, val patterns: HashMap<String, Pattern>, val fixtures: HashMap<String, Value>) {
    private fun serverStateMatches(actualState: HashMap<String, Value>, resolver: Resolver) =
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

    fun matches(httpRequest: HttpRequest, serverState: HashMap<String, Value>): Result {
        val resolver = Resolver(serverState, false, patterns)
        if (!serverStateMatches(serverState, resolver.copy())) {
            return Result.Failure("Facts mismatch", breadCrumb = "FACTS").also { it.updateScenario(this) }
        }
        return httpRequestPattern.matches(httpRequest, resolver).also {
            it.updateScenario(this)
        }
    }

    fun generateHttpResponse(actualFacts: HashMap<String, Value>): HttpResponse =
        scenarioBreadCrumb(this) {
            Resolver(HashMap(), false, patterns)
            val resolver = Resolver(actualFacts, false, patterns)
            val facts = combineFacts(expectedFacts, actualFacts, resolver)

            httpResponsePattern.generateResponse(resolver.copy(factStore = CheckFacts(facts)))
        }

    private fun combineFacts(
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
                            throw ContractException("Couldn't match state values. Expected $expectedValue in key $key, actual value is $actualValue")
                        }
                    }
                }
                key in expected -> combinedServerState[key] = expectedValue
                key in actual -> combinedServerState[key] = actualValue
            }
        }

        return combinedServerState
    }

    fun generateHttpRequest(): HttpRequest =
            scenarioBreadCrumb(this) { httpRequestPattern.generate(Resolver(expectedFacts, false, patterns)) }

    fun matches(httpResponse: HttpResponse): Result {
        val resolver = Resolver(expectedFacts, false, patterns)

        return try {
            httpResponsePattern.matches(httpResponse, resolver).also { it.updateScenario(this) }.let {
                when (it) {
                    is Result.Failure -> it.also { failure -> failure.updateScenario(this) }
                    else -> it
                }
            }
        } catch (exception: Throwable) {
            return Result.Failure("Exception: ${exception.message}")
        }
    }

    private fun newBasedOn(row: Row): List<Scenario> {
        val resolver = Resolver(expectedFacts, false, patterns)

        val newExpectedServerState = newExpectedServerStateBasedOn(row, expectedFacts, fixtures, resolver)
        return httpRequestPattern.newBasedOn(row, resolver).map { newHttpRequestPattern ->
            Scenario(name, newHttpRequestPattern, httpResponsePattern, newExpectedServerState, examples, patterns, fixtures)
        }
    }

    fun generateTestScenarios(): List<Scenario> =
        scenarioBreadCrumb(this) {
            when (examples.size) {
                0 -> listOf(Row())
                else -> examples.flatMap { it.rows }
            }.flatMap { row -> newBasedOn(row) }
        }

    val serverState: Map<String, Value>
        get() = expectedFacts

    fun matchesMock(request: HttpRequest, response: HttpResponse): Result {
        return scenarioBreadCrumb(this) {
            val resolver = Resolver(IgnoreFacts(), true, patterns)
            val requestMatchResult = attempt(breadCrumb = "REQUEST") { httpRequestPattern.matches(request, resolver) }

            if(requestMatchResult is Result.Failure) {
                requestMatchResult.updateScenario(this)
            } else {
                val responseMatchResult = attempt(breadCrumb = "RESPONSE") { httpResponsePattern.matchesMock(response, resolver) }

                if (responseMatchResult is Result.Failure) {
                    responseMatchResult.updateScenario(this)
                }
                else
                    responseMatchResult
            }
        }
    }

    fun matchesMock(response: HttpResponse): Result {
        val resolver = Resolver(IgnoreFacts(), true, patterns)
        return httpResponsePattern.matchesMock(response, resolver).also {
            it.updateScenario(this)
        }
    }

    fun generateHttpResponseFrom(response: HttpResponse?): HttpResponse =
        scenarioBreadCrumb(this) {
            attempt(breadCrumb = "RESPONSE") {
                val resolver = Resolver(expectedFacts, false, patterns)
                HttpResponsePattern(response!!).generateResponse(resolver)
            }
        }

    override fun toString(): String {
        val scenarioDescription = StringBuilder()
        scenarioDescription.append("Scenario: ")
        when {
            name.isNotEmpty() -> scenarioDescription.append("$name ")
        }
        return scenarioDescription.append("$httpRequestPattern").toString()
    }

    fun newBasedOn(scenario: Scenario): Scenario =
        Scenario(this.name, this.httpRequestPattern, this.httpResponsePattern, this.expectedFacts, scenario.examples, this.patterns, this.fixtures)

    fun newBasedOn(suggestions: List<Scenario>) =
        this.newBasedOn(suggestions.find { it.name == this.name } ?: this)

}

fun newExpectedServerStateBasedOn(row: Row, expectedServerState: Map<String, Value>, fixtures: HashMap<String, Value>, resolver: Resolver): HashMap<String, Value> {
    return attempt(errorMessage = "Scenario fact generation failed") {
        HashMap(expectedServerState.mapValues { (key, value) ->
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
}
