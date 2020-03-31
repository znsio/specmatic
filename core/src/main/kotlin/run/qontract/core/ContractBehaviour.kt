package run.qontract.core

import io.cucumber.gherkin.GherkinDocumentBuilder
import io.cucumber.gherkin.Parser
import io.cucumber.messages.IdGenerator
import io.cucumber.messages.IdGenerator.Incrementing
import io.cucumber.messages.Messages.GherkinDocument
import run.qontract.core.pattern.*
import run.qontract.core.pattern.PatternTable.Companion.examplesFrom
import run.qontract.core.utilities.jsonStringToMap
import run.qontract.mock.NoMatchingScenario
import run.qontract.test.TestExecutor
import java.net.URI
import java.util.*

class ContractBehaviour(contractGherkinDocument: GherkinDocument) {
    private val scenarios: List<Scenario> = lex(contractGherkinDocument)
    private var serverState = HashMap<String, Any>()

    constructor(gherkinData: String) : this(parseGherkinString(gherkinData))

    @Throws(Exception::class)
    fun lookup(httpRequest: HttpRequest): HttpResponse {
        try {
            val results = Results()
            scenarios.find {
                it.matches(httpRequest, serverState).also { result ->
                    results.add(result, httpRequest, null)
                } is Result.Success
            }?.let {
                return it.generateHttpResponse(serverState)
            }
            return results.generateErrorHttpResponse()
        } finally {
            serverState = HashMap()
        }
    }

    fun executeTests(suggestions: List<Scenario>, testExecutorFn: TestExecutor): ExecutionInfo {
        val scenariosWithSuggestions = generateTestScenarios(suggestions)
        val executionInfo = ExecutionInfo()
        for (scenario in scenariosWithSuggestions) {
            executeTest(testExecutorFn, scenario.generateTestScenarios(), executionInfo)
        }
        return executionInfo
    }

    fun executeTests(testExecutorFn: TestExecutor): ExecutionInfo {
        val executionInfo = ExecutionInfo()
        for (scenario in scenarios) {
            executeTest(testExecutorFn, scenario.generateTestScenarios(), executionInfo)
        }
        return executionInfo
    }

    private fun executeTest(testExecutor: TestExecutor, testScenarios: List<Scenario>, executionInfo: ExecutionInfo) {
        for (testScenario in testScenarios) {
            executeTest(testExecutor, testScenario, executionInfo)
        }
    }

    private fun executeTest(testExecutor: TestExecutor, scenario: Scenario, executionInfo: ExecutionInfo) {
        testExecutor.setServerState(scenario.serverState)
        val request = scenario.generateHttpRequest()
        try {
            val response = testExecutor.execute(request)
            scenario.matches(response).record(executionInfo, request, response)
        } catch (exception: Throwable) {
            Result.Failure("Error: ${exception.message}")
                    .also { it.updateScenario(scenario) }
                    .record(executionInfo, request, null)
        }
    }

    fun setServerState(serverState: Map<String, Any>) {
        this.serverState.putAll(serverState)
    }

    fun matches(request: HttpRequest, response: HttpResponse): Boolean {
        return scenarios.filter { it.matches(request, serverState) is Result.Success }
                .none { it.matches(response) is Result.Success }
    }

    fun getResponseForMock(request: HttpRequest, response: HttpResponse): HttpResponse {
        try {
            val results = Results()

            for (scenario in scenarios) {
                when (val requestMatches = scenario.matches(request, serverState)) {
                    is Result.Success -> {
                        when (val responseMatches = scenario.matchesMock(response)) {
                            is Result.Success -> return scenario.generateHttpResponseFrom(response)
                            is Result.Failure -> {
                                responseMatches.add("Response didn't match the pattern")
                                        .also { failure -> failure.updateScenario(scenario) }
                                results.add(responseMatches, request, response)
                            }
                        }
                    }
                    is Result.Failure -> {
                        requestMatches.add("Request didn't match the pattern")
                                .also { failure -> failure.updateScenario(scenario) }
                        results.add(requestMatches, request, response)
                    }
                }
            }

            throw NoMatchingScenario(results.generateErrorMessage())
        } finally {
            serverState = HashMap()
        }
    }

    fun generateTestScenarios(suggestions: List<Scenario>) =
            scenarios.map { scenario ->
                scenario.newBasedOn(suggestions)
            }.flatMap { it.generateTestScenarios() }.toList()

    fun generateContractTests(): List<Scenario> =
        scenarios.flatMap { scenario ->
            scenario.copy(examples = emptyList()).generateTestScenarios()
        }
}

private fun plusFixture(fixtures: Map<String, Any>, name: String, info: Any) =
        fixtures.plus(name to info)

private fun plusFixture(fixtures: Map<String, Any>, rest: String): Map<String, Any> {
    val fixtureTokens = breakIntoParts(rest.trim(), 2)

    return when (fixtureTokens.size) {
        2 -> fixtures.plus(fixtureTokens[0] to toFixtureData(fixtureTokens[1]))
//        plusFixture(fixtures, fixtureTokens[0], toFixtureData(fixtureTokens[1]))
        else -> throw ContractParseException("Couldn't parse fixture data: $rest")
    }
}

private fun toFixtureInfo(rest: String): Pair<String, Any> {
    val fixtureTokens = breakIntoParts(rest.trim(), 2)

    return when (fixtureTokens.size) {
        2 -> fixtureTokens[0] to toFixtureData(fixtureTokens[1])
        else -> throw ContractParseException("Couldn't parse fixture data: $rest")
    }
}

private fun toFixtureData(rawData: String): Any = parsedJSON(rawData)?.value ?: rawData

private fun toPatternInfo(rest: String, rowsList: List<GherkinDocument.Feature.TableRow>): Pair<String, Pattern> {
    val tokens = breakIntoParts(rest, 2)

    val patternName = nameToPatternSpec(tokens[0])
    val patternDefinition = tokens.getOrElse(1) { "" }.trim()

    val pattern = when {
        patternDefinition.isEmpty() -> rowsToTabularPattern(rowsList)
        else -> parsedPattern(patternDefinition)
    }
    return Pair(patternName, pattern)
}

private fun toFacts(rest: String, lookupTable: Map<String, Any>): Map<String, Any> {
    val facts = HashMap<String, Any>()

    try {
        facts.putAll(jsonStringToMap(rest).mapValues { it.value ?: "" })
    } catch (notValidJSON: Exception) {
        val factTokens = breakIntoParts(rest, 2)
        val name = factTokens[0]
        val data = factTokens.getOrNull(1)

        facts[name] = data?.let { convertStringToCorrectType(it) } ?: lookupTable.getOrDefault(name, true)
    }

    return facts
}

private fun lexScenario(steps: List<GherkinDocument.Feature.Step>, examplesList: List<GherkinDocument.Feature.Scenario.Examples>, backgroundScenarioInfo: ScenarioInfo): ScenarioInfo {
    val filteredSteps = steps.map { StepInfo(it.text, it.dataTable.rowsList) }.filterNot { it.isEmpty }

    val parsedScenarioInfo = filteredSteps.fold(backgroundScenarioInfo) { scenarioInfo, step ->
        when(step.keyword) {
            in HTTP_METHODS -> {
                step.words.getOrNull(1)?.let {
                    scenarioInfo.copy(httpRequestPattern = scenarioInfo.httpRequestPattern.copy(
                                            urlMatcher = URLMatcher(URI.create(step.rest)),
                                            method = step.keyword.toUpperCase()))
                } ?: throw ContractParseException("Line ${step.line}: $step.text")
            }
            "REQUEST-HEADER" ->
                scenarioInfo.copy(httpRequestPattern = scenarioInfo.httpRequestPattern.copy(headersPattern = plusHeaderPattern(step.rest, scenarioInfo.httpRequestPattern.headersPattern)))
            "RESPONSE-HEADER" ->
                scenarioInfo.copy(httpResponsePattern = scenarioInfo.httpResponsePattern.copy(headersPattern = plusHeaderPattern(step.rest, scenarioInfo.httpResponsePattern.headersPattern)))
            "STATUS" ->
                scenarioInfo.copy(httpResponsePattern = scenarioInfo.httpResponsePattern.copy(status = Integer.valueOf(step.rest)))
            "REQUEST-BODY" ->
                scenarioInfo.copy(httpRequestPattern = scenarioInfo.httpRequestPattern.bodyPattern(step.rest))
            "RESPONSE-BODY" ->
                scenarioInfo.copy(httpResponsePattern = scenarioInfo.httpResponsePattern.bodyPattern(step.rest))
            "FACT" ->
                scenarioInfo.copy(expectedServerState = scenarioInfo.expectedServerState.plus(toFacts(step.rest, scenarioInfo.fixtures)))
            "PATTERN", "JSON" ->
                scenarioInfo.copy(patterns = scenarioInfo.patterns.plus(toPatternInfo(step.rest, step.rowsList)))
            "FIXTURE" ->
                scenarioInfo.copy(fixtures = scenarioInfo.fixtures.plus(toFixtureInfo(step.rest)))
            "FORM-FIELD" ->
                scenarioInfo.copy(httpRequestPattern = scenarioInfo.httpRequestPattern.copy(formFieldsPattern = plusFormFields(scenarioInfo.httpRequestPattern.formFieldsPattern, step.rest, step.rowsList)))
            else -> throw ContractParseException("Couldn't recognise the meaning of this command: $step.text")
        }
    }

    return parsedScenarioInfo.copy(examples = backgroundScenarioInfo.examples.plus(examplesFrom(examplesList)))
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
        2 -> headersPattern.copy(headers = headersPattern.headers.plus(parts[0] to parts[1]))
        else -> headersPattern
    }
}

private fun breakIntoParts(whole: String, partCount: Int) = whole.split("\\s+".toRegex(), partCount)

private val HTTP_METHODS = listOf("GET", "POST", "PUT", "DELETE", "HEAD", "OPTIONS")

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
        Scenario(scenarioInfo.scenarioName, scenarioInfo.httpRequestPattern, scenarioInfo.httpResponsePattern, HashMap(scenarioInfo.expectedServerState), scenarioInfo.examples, HashMap(scenarioInfo.patterns), HashMap(scenarioInfo.fixtures))
    }

private fun lexBackground(featureChildren: List<GherkinDocument.Feature.FeatureChild>): ScenarioInfo =
    background(featureChildren)?.let { feature ->
        lexScenario(feature.background.stepsList, listOf(), ScenarioInfo())
    } ?: ScenarioInfo()

private fun background(featureChildren: List<GherkinDocument.Feature.FeatureChild>) =
    featureChildren.firstOrNull { it.valueCase.name == "BACKGROUND" }

private fun scenarios(featureChildren: List<GherkinDocument.Feature.FeatureChild>) =
        featureChildren.filter { it.valueCase.name != "BACKGROUND" }