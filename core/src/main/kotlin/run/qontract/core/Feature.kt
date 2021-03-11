package run.qontract.core

import io.cucumber.gherkin.GherkinDocumentBuilder
import io.cucumber.gherkin.Parser
import io.cucumber.messages.IdGenerator
import io.cucumber.messages.IdGenerator.Incrementing
import io.cucumber.messages.Messages.GherkinDocument
import run.qontract.core.pattern.*
import run.qontract.core.pattern.Examples.Companion.examplesFrom
import run.qontract.core.utilities.jsonStringToValueMap
import run.qontract.core.value.*
import run.qontract.mock.NoMatchingScenario
import run.qontract.mock.ScenarioStub
import run.qontract.stub.HttpStubData
import run.qontract.test.TestExecutor
import java.io.File
import java.net.URI

fun Feature(gherkinData: String): Feature {
    val gherkinDocument = parseGherkinString(gherkinData)
    return Feature(gherkinDocument)
}

fun Feature(contractGherkinDocument: GherkinDocument): Feature {
    val (name, scenarios) = lex(contractGherkinDocument)
    return Feature(scenarios = scenarios, name = name)
}

data class Feature(val scenarios: List<Scenario> = emptyList(), private var serverState: Map<String, Value> = emptyMap(), val name: String, val testVariables: Map<String, String> = emptyMap(), val testBsaeURLs: Map<String, String> = emptyMap()) {
    fun lookupResponse(httpRequest: HttpRequest): HttpResponse {
        try {
            val resultList = lookupScenario(httpRequest, scenarios)
            return matchingScenario(resultList)?.generateHttpResponse(serverState) ?: Results(resultList.map { it.second }.toMutableList()).withoutFluff().generateErrorHttpResponse()
        } finally {
            serverState = emptyMap()
        }
    }

    fun stubResponse(httpRequest: HttpRequest): HttpResponse {
        try {
            val scenarioSequence = scenarios.asSequence()

            val localCopyOfServerState = serverState
            val resultList = scenarioSequence.zip(scenarioSequence.map {
                it.matchesStub(httpRequest, localCopyOfServerState)
            })
            return matchingScenario(resultList)?.generateHttpResponse(serverState) ?: Results(resultList.map { it.second }.toMutableList()).withoutFluff().generateErrorHttpResponse()
        } finally {
            serverState = emptyMap()
        }
    }

    fun lookupScenario(httpRequest: HttpRequest): List<Scenario> =
        try {
            val resultList = lookupScenario(httpRequest, scenarios)
            val matchingScenarios = matchingScenarios(resultList)

            val firstRealResult = resultList.filterNot { isURLPathMismatch(it.second) }.firstOrNull()
            val resultsExist = resultList.firstOrNull() != null

            when {
                matchingScenarios.isNotEmpty() -> matchingScenarios
                firstRealResult != null -> throw ContractException(resultReport(firstRealResult.second))
                resultsExist -> throw ContractException(PATH_NOT_RECOGNIZED_ERROR)
                else -> throw ContractException("The contract is empty.")
            }
        } finally {
            serverState = emptyMap()
        }

    private fun matchingScenarios(resultList: Sequence<Pair<Scenario, Result>>): List<Scenario> {
        return resultList.filter {
            it.second is Result.Success
        }.map { it.first }.toList()
    }

    private fun matchingScenario(resultList: Sequence<Pair<Scenario, Result>>): Scenario? {
        return resultList.find {
            it.second is Result.Success
        }?.first
    }

    private fun lookupScenario(httpRequest: HttpRequest, scenarios: List<Scenario>): Sequence<Pair<Scenario, Result>> {
        val scenarioSequence = scenarios.asSequence()

        val localCopyOfServerState = serverState
        return scenarioSequence.zip(scenarioSequence.map {
            it.matches(httpRequest, localCopyOfServerState)
        })
    }

    fun executeTests(testExecutorFn: TestExecutor, suggestions: List<Scenario> = emptyList()): Results =
            generateContractTestScenarios(suggestions).fold(Results()) { results, scenario ->
                Results(results = results.results.plus(executeTest(scenario, testExecutorFn)).toMutableList())
            }

    fun setServerState(serverState: Map<String, Value>) {
        this.serverState = this.serverState.plus(serverState)
    }

    fun matches(request: HttpRequest, response: HttpResponse): Boolean {
        return scenarios.firstOrNull { it.matches(request, serverState) is Result.Success }?.matches(response) is Result.Success
    }

    fun matchingStub(request: HttpRequest, response: HttpResponse): HttpStubData {
        try {
            val results = scenarios.map { scenario ->
                try {
                    when(val matchResult = scenario.matchesMock(request, response)) {
                        is Result.Success -> Pair(scenario.resolverAndResponseFrom(response).let { (resolver, response) ->
                            val newRequestType = scenario.httpRequestPattern.generate(request, resolver)
                            val requestTypeWithAncestors =
                                    newRequestType.copy(headersPattern = newRequestType.headersPattern.copy(ancestorHeaders = scenario.httpRequestPattern.headersPattern.pattern))
                            HttpStubData(response = response, resolver = resolver, requestType = requestTypeWithAncestors)
                        }, Result.Success())
                        is Result.Failure -> {
                            Pair(null, matchResult.updateScenario(scenario))
                        }
                    }
                } catch (contractException: ContractException) {
                    Pair(null, contractException.failure())
                }
            }

            return results.find {
                it.first != null
            }?.let { it.first as HttpStubData } ?: throw NoMatchingScenario(failureResults(results).withoutFluff().report())
        } finally {
            serverState = emptyMap()
        }
    }

    fun failureResults(results: List<Pair<HttpStubData?, Result>>): Results =
            Results(results.map { it.second }.filter { it is Result.Failure }.toMutableList())

    fun generateContractTestScenarios(suggestions: List<Scenario>): List<Scenario> =
        scenarios.map { it.newBasedOn(suggestions) }.flatMap {
            it.generateTestScenarios(testVariables, testBsaeURLs)
        }

    fun generateBackwardCompatibilityTestScenarios(): List<Scenario> =
        scenarios.flatMap { scenario ->
            scenario.copy(examples = emptyList()).generateTestScenarios()
        }

    fun assertMatchesMockKafkaMessage(kafkaMessage: KafkaMessage) {
        val result = matchesMockKafkaMessage(kafkaMessage)
        if (result is Result.Failure)
            throw NoMatchingScenario(resultReport(result))
    }

    fun matchesMockKafkaMessage(kafkaMessage: KafkaMessage): Result {
        val results = scenarios.asSequence().map {
            it.matchesMock(kafkaMessage)
        }

        return results.find { it is Result.Success } ?: results.firstOrNull() ?: Result.Failure("No match found, couldn't check the message")
    }

    fun matchingStub(scenarioStub: ScenarioStub): HttpStubData =
            matchingStub(scenarioStub.request, scenarioStub.response).copy(delayInSeconds = scenarioStub.delayInSeconds)

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
    val fixtureTokens = breakIntoPartsMaxLength(rest.trim(), 2)

    if(fixtureTokens.size != 2)
        throw ContractException("Couldn't parse fixture data: $rest")

    return Pair(fixtureTokens[0], toFixtureData(fixtureTokens[1]))
}

private fun toFixtureData(rawData: String): Value = parsedJSON(rawData)

internal fun stringOrDocString(string: String?, step: StepInfo): String {
    val trimmed = string?.trim() ?: ""
    return trimmed.ifEmpty { step.docString }
}
private fun toPatternInfo(step: StepInfo, rowsList: List<GherkinDocument.Feature.TableRow>): Pair<String, Pattern> {
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

private fun lexScenario(steps: List<GherkinDocument.Feature.Step>, examplesList: List<GherkinDocument.Feature.Scenario.Examples>, featureTags: List<GherkinDocument.Feature.Tag>, backgroundScenarioInfo: ScenarioInfo): ScenarioInfo {
    val filteredSteps = steps.map { StepInfo(it.text, it.dataTable.rowsList, it) }.filterNot { it.isEmpty }

    val parsedScenarioInfo = filteredSteps.fold(backgroundScenarioInfo) { scenarioInfo, step ->
        when(step.keyword) {
            in HTTP_METHODS -> {
                step.words.getOrNull(1)?.let {
                    val urlMatcher = try {
                        toURLMatcherWithOptionalQueryParams(URI.create(step.rest))
                    } catch (e: Throwable) {
                        throw Exception("Could not parse the contract URL \"${step.rest}\" in scenario \"${scenarioInfo.scenarioName}\"", e)
                    }

                    scenarioInfo.copy(httpRequestPattern = scenarioInfo.httpRequestPattern.copy(urlMatcher = urlMatcher, method = step.keyword.toUpperCase()))
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
                scenarioInfo.copy(patterns = scenarioInfo.patterns.plus(toPatternInfo(step, step.rowsList)))
            "FIXTURE" ->
                scenarioInfo.copy(fixtures = scenarioInfo.fixtures.plus(toFixtureInfo(step.rest)))
            "FORM-FIELD" ->
                scenarioInfo.copy(httpRequestPattern = scenarioInfo.httpRequestPattern.copy(formFieldsPattern = plusFormFields(scenarioInfo.httpRequestPattern.formFieldsPattern, step.rest, step.rowsList)))
            "REQUEST-PART" ->
                scenarioInfo.copy(httpRequestPattern = scenarioInfo.httpRequestPattern.copy(multiPartFormDataPattern = scenarioInfo.httpRequestPattern.multiPartFormDataPattern.plus(toFormDataPart(step))))
            "KAFKA-MESSAGE" ->
                scenarioInfo.copy(kafkaMessage = toAsyncMessage(step))
            "VALUE" ->
                scenarioInfo.copy(references = values(step.rest, scenarioInfo.references, backgroundScenarioInfo.references))
            "SET" ->
                scenarioInfo.copy(setters = setters(step.rest, backgroundScenarioInfo.setters, scenarioInfo.setters))
            else -> {
                val location = when {
                    step.raw.hasLocation() -> " at line ${step.raw.location.line}"
                    else -> ""
                }

                throw ContractException("""Invalid syntax$location: ${step.raw.keyword.trim()} ${step.raw.text} -> keyword "${step.originalKeyword}" not recognised.""")
            }
        }
    }

    val tags = featureTags.map { tag -> tag.name }
    val ignoreFailure = when {
        tags.asSequence().map { it.toUpperCase() }.contains("@WIP") -> true
        else -> false
    }

    return parsedScenarioInfo.copy(examples = backgroundScenarioInfo.examples.plus(examplesFrom(examplesList)), ignoreFailure = ignoreFailure)
}

fun setters(rest: String, backgroundSetters: Map<String, String>, scenarioSetters: Map<String, String>): Map<String, String> {
    val parts = breakIntoPartsMaxLength(rest, 3)

    if(parts.size != 3 || parts[1] != "=")
        throw ContractException("Setter syntax is incorrect in \"$rest\". Syntax should be \"Then set <variable> = <selector>\"")

    val variableName = parts[0]
    val selector = parts[2]

    return backgroundSetters.plus(scenarioSetters).plus(variableName to selector)
}

fun values(
    rest: String,
    scenarioReferences: Map<String, References>,
    backgroundReferences: Map<String, References>,
): Map<String, References> {
    val parts = breakIntoPartsMaxLength(rest, 3)

    if(parts.size != 3 || parts[1] != "from")
        throw ContractException("Incorrect syntax for value statement: $rest - it should be \"Given value <value name> from <qontract file name>\"")

    val valueStoreName = parts[0]
    val qontractFileName = parts[2]

    if(!File(qontractFileName).exists())
        throw ContractException("File $qontractFileName does not exist")

    return backgroundReferences.plus(scenarioReferences).plus(valueStoreName to References(valueStoreName, qontractFileName))
}

fun toAsyncMessage(step: StepInfo): KafkaMessagePattern {
    val parts = breakIntoPartsMaxLength(step.rest, 3)

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
    val parts = breakIntoPartsMaxLength(step.rest, 4)

    if(parts.size < 2)
        throw ContractException("There must be at least 2 words after request-part in $step.line")

    val (name, content) = parts.slice(0..1)

    return when {
        content.startsWith("@") -> {
            val contentType = parts.getOrNull(2)
            val contentEncoding = parts.getOrNull(3)

            val filename = content.removePrefix("@")

            MultiPartFilePattern(name, parsedPattern(filename), contentType, contentEncoding)
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
    return when(val stringData = stringOrDocString(step.rest, step)) {
        "" -> {
            if(step.rowsList.isEmpty()) throw ContractException("Not enough information to describe a type in $step")
            rowsToTabularPattern(step.rowsList)
        }
        else -> parsedPattern(stringData)
    }
}

fun plusFormFields(formFields: Map<String, Pattern>, rest: String, rowsList: List<GherkinDocument.Feature.TableRow>): Map<String, Pattern> =
    formFields.plus(when(rowsList.size) {
        0 -> toQueryParams(rest).map { (key, value) -> key to value }
        else -> rowsList.map { row -> row.cellsList[0].value to row.cellsList[1].value }
    }.map { (key, value) -> key to parsedPattern(value) }.toMap())

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

private val HTTP_METHODS = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")

internal fun parseGherkinString(gherkinData: String): GherkinDocument {
    val idGenerator: IdGenerator = Incrementing()
    val parser = Parser(GherkinDocumentBuilder(idGenerator))
    return parser.parse(gherkinData).build()
}

internal fun lex(gherkinDocument: GherkinDocument): Pair<String, List<Scenario>> =
        Pair(gherkinDocument.feature.name, lex(gherkinDocument.feature.childrenList))

internal fun lex(featureChildren: List<GherkinDocument.Feature.FeatureChild>): List<Scenario> {
    return scenarios(featureChildren).map { featureChild ->
        if (featureChild.scenario.name.isBlank())
            throw ContractException("Error at line ${featureChild.scenario.location.line}: scenario name must not be empty")

        val backgroundInfoCopy = (background(featureChildren)?.let { feature ->
            lexScenario(feature.background.stepsList, listOf(), emptyList(), ScenarioInfo())
        } ?: ScenarioInfo()).copy(scenarioName = featureChild.scenario.name)

        lexScenario(
            featureChild.scenario.stepsList,
            featureChild.scenario.examplesList,
            featureChild.scenario.tagsList,
            backgroundInfoCopy
        )
    }.map { scenarioInfo ->
        Scenario(
            scenarioInfo.scenarioName,
            scenarioInfo.httpRequestPattern,
            scenarioInfo.httpResponsePattern,
            scenarioInfo.expectedServerState,
            scenarioInfo.examples,
            scenarioInfo.patterns,
            scenarioInfo.fixtures,
            scenarioInfo.kafkaMessage,
            scenarioInfo.ignoreFailure,
            scenarioInfo.references,
            scenarioInfo.setters
        )
    }
}

private fun background(featureChildren: List<GherkinDocument.Feature.FeatureChild>) =
    featureChildren.firstOrNull { it.valueCase.name == "BACKGROUND" }

private fun scenarios(featureChildren: List<GherkinDocument.Feature.FeatureChild>) =
        featureChildren.filter { it.valueCase.name != "BACKGROUND" }

fun toGherkinFeature(stub: NamedStub): String = toGherkinFeature("New Feature", listOf(stub))

private fun stubToClauses(namedStub: NamedStub): Pair<List<GherkinClause>, ExampleDeclarations> {
    return when (namedStub.stub.kafkaMessage) {
        null -> {
            val (requestClauses, typesFromRequest, examples) = toGherkinClauses(namedStub.stub.request)

            for(message in examples.messages) {
                println(message)
            }

            val (responseClauses, allTypes, _) = toGherkinClauses(namedStub.stub.response, typesFromRequest)
            val typeClauses = toGherkinClauses(allTypes)
            Pair(typeClauses.plus(requestClauses).plus(responseClauses), examples)
        }
        else -> Pair(toGherkinClauses(namedStub.stub.kafkaMessage), UseExampleDeclarations())
    }
}

data class GherkinScenario(val scenarioName: String, val clauses: List<GherkinClause>)

fun toGherkinFeature(featureName: String, stubs: List<NamedStub>): String {
    val groupedStubs = stubs.map { stub ->
        val (clauses, examples) = stubToClauses(stub)
        val commentedExamples = addCommentsToExamples(examples, stub)

        Pair(GherkinScenario(stub.name, clauses), listOf(commentedExamples))
    }.fold(emptyMap<GherkinScenario, List<ExampleDeclarations>>(),
        { groups, (scenario, examples) ->
            groups.plus(scenario to groups.getOrDefault(scenario, emptyList()).plus(examples))
        })

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
