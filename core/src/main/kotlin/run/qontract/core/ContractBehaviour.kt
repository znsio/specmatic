package run.qontract.core

import io.cucumber.gherkin.GherkinDocumentBuilder
import io.cucumber.gherkin.Parser
import io.cucumber.messages.IdGenerator
import io.cucumber.messages.IdGenerator.Incrementing
import io.cucumber.messages.Messages.GherkinDocument
import run.qontract.core.pattern.*
import run.qontract.core.pattern.PatternTable.Companion.examplesFrom
import run.qontract.core.pattern.PatternTable.Companion.fromPSV
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

}

private fun storeFixture(fixtures: HashMap<String, Any>, name: String, info: Any) {
    fixtures[name] = info
}

private fun storeFixture(fixtures: HashMap<String, Any>, rest: String) {
    val fixtureTokens = rest.trim().split("\\s+".toRegex(), 2)

    if (fixtureTokens.size < 2)
        throw ContractParseException("Couldn't parse fixture data: $rest")

    storeFixture(fixtures, fixtureTokens[0], toFixtureData(fixtureTokens[1]))
}

private fun toFixtureData(rawData: String): Any = parsedJSON(rawData)?.value ?: rawData
private fun storePattern(patterns: HashMap<String, Pattern>, rest: String, rowsList: List<GherkinDocument.Feature.TableRow>) {
    val tokens = breakIntoParts(rest, 2)

    val patternName = nameToPatternSpec(tokens[0])
    val patternSpec = tokens.getOrElse(1) { "" }.trim()

    patterns[patternName] = when {
        patternSpec.isEmpty() -> rowsToPattern(rowsList)
        else -> parsedPattern(patternSpec)
    }
}

private fun toFacts(rest: String, fixtures: HashMap<String, Any>): HashMap<String, Any> {
    val facts = HashMap<String, Any>()

    try {
        facts.putAll(jsonStringToMap(rest).mapValues { it.value ?: "" })
    } catch (notValidJSON: Exception) {
        val factTokens = breakIntoParts(rest, 2)
        val name = factTokens[0]
        val data = factTokens.getOrNull(1)

        facts[name] = data?.let { convertStringToCorrectType(it) } ?: fixtures.getOrDefault(name, true)
    }

    return facts
}

private fun lexScenario(steps: MutableList<GherkinDocument.Feature.Step>, examplesList: List<GherkinDocument.Feature.Scenario.Examples>, scenarioInfo: ScenarioInfo): Scenario {
    for (step in steps.map { StepInfo(it.text, it.dataTable.rowsList) }.filterNot { it.isEmpty }) {
        when (step.keyword) {
            in HTTP_METHODS -> {
                step.words.getOrNull(1)?.let {
                    scenarioInfo.httpRequestPattern.updateWith(URLMatcher(URI.create(step.rest)))
                    scenarioInfo.httpRequestPattern.updateMethod(step.keyword)
                } ?: throw ContractParseException("Line ${step.line}: $step.text")
            }
            "REQUEST-HEADER" -> addToHeaderPattern(step.rest, scenarioInfo.httpRequestPattern.headersPattern)
            "STATUS" -> scenarioInfo.httpResponsePattern.status = Integer.valueOf(step.rest)
            "REQUEST-BODY" -> scenarioInfo.httpRequestPattern.setBodyPattern(step.rest)
            "RESPONSE-HEADER" -> addToHeaderPattern(step.rest, scenarioInfo.httpResponsePattern.headersPattern)
            "RESPONSE-BODY" -> scenarioInfo.httpResponsePattern.setBodyPattern(step.rest)
            "FACT" -> scenarioInfo.expectedServerState.putAll(toFacts(step.rest, scenarioInfo.fixtures))
            "PATTERN" -> storePattern(scenarioInfo.patterns, step.rest, step.rowsList)
            "FIXTURE" -> storeFixture(scenarioInfo.fixtures, step.rest)
            else -> throw ContractParseException("Couldn't recognise the meaning of this command: $step.text")
        }
    }

    scenarioInfo.examples.addAll(examplesFrom(examplesList).toMutableList())
    return Scenario(scenarioInfo.scenarioName, scenarioInfo.httpRequestPattern, scenarioInfo.httpResponsePattern, scenarioInfo.expectedServerState, scenarioInfo.examples, scenarioInfo.patterns, scenarioInfo.fixtures)
}

fun addToHeaderPattern(rest: String, headersPattern: HttpHeadersPattern) {
    breakIntoParts(rest, 2).takeIf { it.size == 2 }?.let { headersPattern.add(it[0] to it[1]) }
}

private fun breakIntoParts(piece: String, parts: Int) = piece.split("\\s+".toRegex(), parts)

private val HTTP_METHODS = listOf("GET", "POST", "PUT", "DELETE", "HEAD", "OPTIONS")
internal fun parseGherkinString(gherkinData: String): GherkinDocument {
    val idGenerator: IdGenerator = Incrementing()
    val parser = Parser(GherkinDocumentBuilder(idGenerator))
    return parser.parse(gherkinData).build()
}

internal fun lex(gherkinDocument: GherkinDocument): List<Scenario> {
    val featureChildren = gherkinDocument.feature.childrenList

    val scenarioInfo = ScenarioInfo()

    featureChildren.find { it.valueCase.name == "BACKGROUND" }?.let {
        scenarioInfo.scenarioName = it.scenario.name
        val backgroundTable = fromPSV(it.background.description)
        if (!backgroundTable.isEmpty) {
            scenarioInfo.examples.add(backgroundTable)
        }
        lexScenario(it.background.stepsList, listOf(), scenarioInfo)
    }

    return featureChildren.filter { it.valueCase.name != "BACKGROUND" }.map {
        scenarioInfo.scenarioName = it.scenario.name
        lexScenario(it.scenario.stepsList, it.scenario.examplesList, scenarioInfo.deepCopy())
    }
}