package run.qontract.core

import io.cucumber.gherkin.GherkinDocumentBuilder
import io.cucumber.gherkin.Parser
import io.cucumber.messages.IdGenerator
import io.cucumber.messages.IdGenerator.Incrementing
import io.cucumber.messages.Messages.GherkinDocument
import run.qontract.core.pattern.*
import run.qontract.core.pattern.Examples.Companion.examplesFrom
import run.qontract.core.utilities.jsonStringToValueMap
import run.qontract.core.value.KafkaMessage
import run.qontract.core.value.StringValue
import run.qontract.core.value.True
import run.qontract.core.value.Value
import run.qontract.mock.MockScenario
import run.qontract.mock.NoMatchingScenario
import run.qontract.test.TestExecutor
import java.net.URI

data class ContractBehaviour(val scenarios: List<Scenario> = emptyList(), private var serverState: Map<String, Value> = emptyMap<String, Value>()) {
    constructor(contractGherkinDocument: GherkinDocument) : this(lex(contractGherkinDocument))
    constructor(gherkinData: String) : this(parseGherkinString(gherkinData))

    fun lookupResponse(httpRequest: HttpRequest): HttpResponse {
        try {
            val resultList = lookupScenario(httpRequest, scenarios)
            return matchingScenario(resultList)?.generateHttpResponse(serverState) ?: errorResponse(resultList, httpRequest)
        } finally {
            serverState = emptyMap()
        }
    }

    fun lookupAllResponses(httpRequest: HttpRequest): List<HttpResponse> =
        try {
            val scenarios = lookupScenario(httpRequest, scenarios)
            matchingScenario(scenarios)?.generateHttpResponses(serverState) ?: listOf(errorResponse(scenarios, httpRequest))
        } finally {
            serverState = emptyMap()
        }

    fun lookupScenario(httpRequest: HttpRequest): Scenario =
        try {
            val scenarios = lookupScenario(httpRequest, scenarios)
            val matchingScenario = matchingScenario(scenarios)
            matchingScenario ?: scenarios.firstOrNull()?.second?.let { throw ContractException(resultReport(it), scenario = matchingScenario) } ?: throw ContractException("The contract is empty.")
        } finally {
            serverState = emptyMap()
        }

    private fun errorResponse(resultList: Sequence<Pair<Scenario, Result>>, httpRequest: HttpRequest) =
            Results(resultList.map { Triple(it.second, httpRequest, null) }.toMutableList()).generateErrorHttpResponse()

    private fun matchingScenario(resultList: Sequence<Pair<Scenario, Result>>): Scenario? {
        return resultList.find {
            it.second is Result.Success
        }?.first
    }

    private fun lookupScenario(httpRequest: HttpRequest, scenarios: List<Scenario>): Sequence<Pair<Scenario, Result>> {
        val scenarioSequence = scenarios.asSequence()

        return scenarioSequence.zip(scenarioSequence.map {
            it.matches(httpRequest, serverState)
        })
    }

    fun executeTests(testExecutorFn: TestExecutor, suggestions: List<Scenario> = emptyList()): Results =
            generateTestScenarios(suggestions).fold(Results()) { results, scenario ->
                Results(results = results.results.plus(executeTest(scenario, testExecutorFn)).toMutableList())
            }

    fun setServerState(serverState: Map<String, Value>) {
        this.serverState = this.serverState.plus(serverState)
    }

    fun matches(request: HttpRequest, response: HttpResponse): Boolean {
        return scenarios.filter { it.matches(request, serverState) is Result.Success }
                .none { it.matches(response) is Result.Success }
    }

    fun matchingMockResponse(request: HttpRequest, response: HttpResponse): Triple<Resolver, Scenario, HttpResponse> {
        try {
            val results = Results()

            for (scenario in scenarios) {
                try {
                    when(val mockMatches = scenario.matchesMock(request, response)) {
                        is Result.Success -> return scenario.resolverAndResponseFrom(response).let {
                            Triple(it.first, scenario, it.second)
                        }
                        is Result.Failure -> {
                            results.add(mockMatches
                                    .updateScenario(scenario), request, response)
                        }
                    }
                } catch (contractException: ContractException) {
                    results.add(contractException.result(), request, response)
                }
            }

            throw NoMatchingScenario(results.report())
        } finally {
            serverState = emptyMap()
        }
    }

    fun generateTestScenarios(suggestions: List<Scenario>): List<Scenario> =
        scenarios.map { it.newBasedOn(suggestions) }.flatMap { it.generateTestScenarios() }

    fun generateTestScenarios(): List<Scenario> =
        scenarios.flatMap { scenario ->
            scenario.copy(examples = emptyList()).generateTestScenarios()
        }

    fun assertMatchesMockKafkaMessage(kafkaMessage: KafkaMessage) {
        val results = scenarios.asSequence().map {
            it.matchesMock(kafkaMessage)
        }

        if(results.none { it is Result.Success })
            throw NoMatchingScenario(resultReport(results.first()))
    }

    fun matchingMockResponse(mockScenario: MockScenario): Triple<Resolver, Scenario, HttpResponse> =
            matchingMockResponse(mockScenario.request, mockScenario.response)

    fun clearServerState() {
        serverState = emptyMap()
    }

    fun lookupKafkaScenario(olderKafkaMessagePattern: KafkaMessagePattern, olderResolver: Resolver): Sequence<Pair<Scenario, Result>> {
        try {
            return scenarios.asSequence()
                    .filter { it.kafkaMessagePattern != null }
                    .map { newerScenario ->
                        Pair(newerScenario, olderKafkaMessagePattern.encompasses(newerScenario.kafkaMessagePattern as KafkaMessagePattern, newerScenario.resolver, olderResolver))
                    }
        } finally {
            serverState = emptyMap()
        }
    }
}

private fun toFixtureInfo(rest: String): Pair<String, Value> {
    val fixtureTokens = breakIntoParts(rest.trim(), 2)

    if(fixtureTokens.size != 2)
        throw ContractException("Couldn't parse fixture data: $rest")

    return Pair(fixtureTokens[0], toFixtureData(fixtureTokens[1]))
}

private fun toFixtureData(rawData: String): Value = parsedJSONStructure(rawData)

private fun toPatternInfo(rest: String, rowsList: List<GherkinDocument.Feature.TableRow>): Pair<String, Pattern> {
    val tokens = breakIntoParts(rest, 2)

    val patternName = withPatternDelimiters(tokens[0])

    val pattern = when(val patternDefinition = tokens.getOrElse(1) { "" }.trim()) {
        "" -> rowsToTabularPattern(rowsList)
        else -> parsedPattern(patternDefinition)
    }

    return Pair(patternName, pattern)
}

private fun toFacts(rest: String, fixtures: Map<String, Value>): Map<String, Value> {
    return try {
        jsonStringToValueMap(rest)
    } catch (notValidJSON: Exception) {
        val factTokens = breakIntoParts(rest, 2)
        val name = factTokens[0]
        val data = factTokens.getOrNull(1)?.let { StringValue(it) } ?: fixtures.getOrDefault(name, True)

        mapOf(name to data)
    }
}

private fun lexScenario(steps: List<GherkinDocument.Feature.Step>, examplesList: List<GherkinDocument.Feature.Scenario.Examples>, backgroundScenarioInfo: ScenarioInfo): ScenarioInfo {
    val filteredSteps = steps.map { StepInfo(it.text, it.dataTable.rowsList) }.filterNot { it.isEmpty }

    val parsedScenarioInfo = filteredSteps.fold(backgroundScenarioInfo) { scenarioInfo, step ->
        when(step.keyword) {
            in HTTP_METHODS -> {
                step.words.getOrNull(1)?.let {
                    scenarioInfo.copy(httpRequestPattern = scenarioInfo.httpRequestPattern.copy(
                                            urlMatcher = toURLPattern(URI.create(step.rest)),
                                            method = step.keyword.toUpperCase()))
                } ?: throw ContractException("Line ${step.line}: $step.text")
            }
            "REQUEST-HEADER" ->
                scenarioInfo.copy(httpRequestPattern = scenarioInfo.httpRequestPattern.copy(headersPattern = plusHeaderPattern(step.rest, scenarioInfo.httpRequestPattern.headersPattern)))
            "RESPONSE-HEADER" ->
                scenarioInfo.copy(httpResponsePattern = scenarioInfo.httpResponsePattern.copy(headersPattern = plusHeaderPattern(step.rest, scenarioInfo.httpResponsePattern.headersPattern)))
            "STATUS" ->
                scenarioInfo.copy(httpResponsePattern = scenarioInfo.httpResponsePattern.copy(status = Integer.valueOf(step.rest)))
            "REQUEST-BODY" ->
                scenarioInfo.copy(httpRequestPattern = scenarioInfo.httpRequestPattern.copy(body = toPattern(step)))
            "RESPONSE-BODY" ->
                scenarioInfo.copy(httpResponsePattern = scenarioInfo.httpResponsePattern.bodyPattern(toPattern(step)))
            "FACT" ->
                scenarioInfo.copy(expectedServerState = scenarioInfo.expectedServerState.plus(toFacts(step.rest, scenarioInfo.fixtures)))
            "TYPE", "PATTERN", "JSON" ->
                scenarioInfo.copy(patterns = scenarioInfo.patterns.plus(toPatternInfo(step.rest, step.rowsList)))
            "FIXTURE" ->
                scenarioInfo.copy(fixtures = scenarioInfo.fixtures.plus(toFixtureInfo(step.rest)))
            "FORM-FIELD" ->
                scenarioInfo.copy(httpRequestPattern = scenarioInfo.httpRequestPattern.copy(formFieldsPattern = plusFormFields(scenarioInfo.httpRequestPattern.formFieldsPattern, step.rest, step.rowsList)))
            "REQUEST-PART" -> {
                scenarioInfo.copy(httpRequestPattern = scenarioInfo.httpRequestPattern.copy(multiPartFormDataPattern = scenarioInfo.httpRequestPattern.multiPartFormDataPattern.plus(toFormDataPart(step))))
            }
            "KAFKA-MESSAGE" -> {
                scenarioInfo.copy(kafkaMessage = toAsyncMessage(step))
            }
            else -> throw ContractException("Couldn't recognise the meaning of this command: $step.text")
        }
    }

    return parsedScenarioInfo.copy(examples = backgroundScenarioInfo.examples.plus(examplesFrom(examplesList)))
}

fun toAsyncMessage(step: StepInfo): KafkaMessagePattern {
    val parts = breakIntoParts(step.rest, 3)

    return when (parts.size) {
        2 -> {
            val (name, type) = parts
            KafkaMessagePattern(name, value = parsedPattern(type))
        }
        3 -> {
            val (name, key, contentType) = parts
            KafkaMessagePattern(name, parsedPattern(key), parsedPattern(contentType))
        }
        else -> throw ContractException("The message keyword must have either 2 params (topic, value) or 3 (topic, key, value)")
    }
}

fun toFormDataPart(step: StepInfo): MultiPartFormDataPattern {
    val parts = breakIntoParts(step.rest, 4)

    if(parts.size < 2)
        throw ContractException("There must be at least 2 words after request-part in $step.line")

    val (name, content) = parts.slice(0..1)

    return when {
        content.startsWith("@") -> {
            val contentType = parts.getOrNull(2)
            val contentEncoding = parts.getOrNull(3)

            MultiPartFilePattern(name, content, contentType, contentEncoding)
        }
        isPatternToken(content) -> {
            MultiPartContentPattern(name, parsedPattern(content))
        }
        else -> {
            MultiPartContentPattern(name, ExactValuePattern(parsedValue(content)))
        }
    }
}

fun toPattern(step: StepInfo): Pattern {
    return when(val trimmedRest = step.rest.trim()) {
        "" -> {
            if(step.rowsList.isEmpty()) throw ContractException("Not enough information to describe a type in $step")
            rowsToTabularPattern(step.rowsList)
        }
        else -> parsedPattern(trimmedRest)
    }
}

fun plusFormFields(formFields: Map<String, Pattern>, rest: String, rowsList: List<GherkinDocument.Feature.TableRow>): Map<String, Pattern> =
    formFields.plus(when(rowsList.size) {
        0 -> toQueryParams(rest).map { (key, value) -> key to value }
        else -> rowsList.map { row -> row.cellsList[0].value to row.cellsList[1].value }
    }.map { (key, value) -> key to parsedPattern(value) }.toMap())

private fun toQueryParams(rest: String) = rest.split("&")
        .map { breakIntoParts(it, 2) }

fun plusHeaderPattern(rest: String, headersPattern: HttpHeadersPattern): HttpHeadersPattern {
    val parts = breakIntoParts(rest, 2)

    return when (parts.size) {
        2 -> headersPattern.copy(pattern = headersPattern.pattern.plus(toPatternPair(parts[0], parts[1])))
        1 -> throw ContractException("Header $parts[0] should have a value")
        else -> throw ContractException("Unrecognised header params $rest")
    }
}

fun toPatternPair(key: String, value: String): Pair<String, Pattern> = key to parsedPattern(value)

private fun breakIntoParts(whole: String, partCount: Int) = whole.split("\\s+".toRegex(), partCount)

private val HTTP_METHODS = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")

internal fun parseGherkinString(gherkinData: String): GherkinDocument {
    val idGenerator: IdGenerator = Incrementing()
    val parser = Parser(GherkinDocumentBuilder(idGenerator))
    return parser.parse(gherkinData).build()
}

internal fun lex(gherkinDocument: GherkinDocument): List<Scenario> =
        lex(gherkinDocument.feature.childrenList)

internal fun lex(featureChildren: List<GherkinDocument.Feature.FeatureChild>): List<Scenario> =
    lex(featureChildren, lexBackground(featureChildren))

internal fun lex(featureChildren: List<GherkinDocument.Feature.FeatureChild>, backgroundInfo: ScenarioInfo): List<Scenario> =
    scenarios(featureChildren).map { feature ->
        val backgroundInfoCopy = backgroundInfo.copy(scenarioName = feature.scenario.name)
        lexScenario(feature.scenario.stepsList, feature.scenario.examplesList, backgroundInfoCopy)
    }.map { scenarioInfo ->
        if(scenarioInfo.scenarioName.isBlank())
            throw ContractException("A scenario name must not be empty. The contract has a scenario without a name.")

        Scenario(scenarioInfo.scenarioName, scenarioInfo.httpRequestPattern, scenarioInfo.httpResponsePattern, scenarioInfo.expectedServerState, scenarioInfo.examples, scenarioInfo.patterns, scenarioInfo.fixtures, scenarioInfo.kafkaMessage)
    }

private fun lexBackground(featureChildren: List<GherkinDocument.Feature.FeatureChild>): ScenarioInfo =
    background(featureChildren)?.let { feature ->
        lexScenario(feature.background.stepsList, listOf(), ScenarioInfo())
    } ?: ScenarioInfo()

private fun background(featureChildren: List<GherkinDocument.Feature.FeatureChild>) =
    featureChildren.firstOrNull { it.valueCase.name == "BACKGROUND" }

private fun scenarios(featureChildren: List<GherkinDocument.Feature.FeatureChild>) =
        featureChildren.filter { it.valueCase.name != "BACKGROUND" }

fun executeTest(scenario: Scenario, testExecutor: TestExecutor): Triple<Result, HttpRequest, HttpResponse?> {
    testExecutor.setServerState(scenario.serverState)

    val request = scenario.generateHttpRequest()

    return try {
        val response = testExecutor.execute(request)
        when {
            response.headers.get("X-Qontract-Result") == "failure" -> {
                Triple(Result.Failure(response.body?.displayableValue() ?: ""), request, response)
            }
            else -> Triple(scenario.matches(response), request, response)
        }
    }
    catch(contractException: ContractException) {
        Triple(contractException.result().also { it.updateScenario(scenario) }, request, null)
    }
    catch(throwable: Throwable) {
        Triple(Result.Failure("Error: ${throwable.message}").also { it.updateScenario(scenario) }, request, null)
    }
}
