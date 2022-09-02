package application

import `in`.specmatic.conversions.OpenApiSpecification
import `in`.specmatic.core.*
import `in`.specmatic.core.log.logger
import `in`.specmatic.core.pattern.parsedJSONArray
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.Value
import `in`.specmatic.mock.NoMatchingScenario
import `in`.specmatic.mock.ScenarioStub
import `in`.specmatic.mock.mockFromJSON
import picocli.CommandLine
import java.io.File
import java.net.URI
import java.util.concurrent.Callable

@CommandLine.Command(name = "validate-for-stubs", mixinStandardHelpOptions = true)
class ValidateForStubs : Callable<Unit> {
    @CommandLine.Parameters(index = "0", description = ["Contract path"])
    lateinit var contractPath: String

    @CommandLine.Parameters(index = "1", description = ["Log dir path"])
    lateinit var logDirPath: String

    @CommandLine.Parameters(index = "2", description = ["urlPathFilter"])
    lateinit var urlPathFilter: String

    override fun call() {
        val feature = OpenApiSpecification.fromFile(contractPath).toFeature()

        val urlMatchers: List<Pair<URLMatcher, Resolver>> = findMatchingURLMatchers(feature)

        val stubList = parsedJSONArray(File("http-json.log").readText())

        val stubsMatchingURLs: Sequence<Pair<ScenarioStub, ScenarioStub>> = stubList.list.asSequence().mapNotNull {
            val log = it as JSONObjectValue

            val pathLog = log.findFirstChildByPath("http-request.path")
            val stubRequestPathLog = if(log.findFirstChildByPath("http-request.path")?.toStringLiteral() in listOf("/_specmatic/expectations", "/_qontract/expectations"))
                                            log.findFirstChildByPath("http-request.body.http-request.path") else null

            if (stubRequestPathLog != null) {
                matchExpectationLog(stubRequestPathLog, log, urlMatchers)?.let { Pair(mockFromJSON(log.jsonObject), it) }
            }
            else if (pathLog != null) {
                matchTopLevelLog(pathLog, urlMatchers, log)?.let { Pair(mockFromJSON(log.jsonObject), it) }
            }
            else
                null
        }

        val matchFailures: Sequence<Triple<Result, ScenarioStub, ScenarioStub>> = stubsMatchingURLs.map { (container, matchingScenario) ->
            try {
                feature.matchingStub(matchingScenario.request, matchingScenario.response)
                Triple(Result.Success(), container, matchingScenario)
            } catch(e: NoMatchingScenario) {
                Triple(e.results.toResultIfAny(), container, matchingScenario)
            }
        }.filter {
            it.first is Result.Failure
        }

        if(matchFailures.firstOrNull() == null)
            logger.log("The contract is compatible with all stubs")
        else {
            logger.log("The contract is not compatible with the following:")
            matchFailures.forEach { (result, container, _) ->
                logger.log(container.toJSON().toStringLiteral())
                logger.log(result.reportString())
                logger.newLine()
                logger.newLine()
            }
        }
    }

    private fun matchExpectationLog(
        stubRequestPathLog: Value,
        log: JSONObjectValue,
        urlMatchers: List<Pair<URLMatcher, Resolver>>
    ): ScenarioStub? {
        val path = stubRequestPathLog.toStringLiteral()
        val body = log.findFirstChildByPath("http-request.body") as JSONObjectValue
        return if (urlMatchers.any { (matcher, resolver) ->
                matcher.matches(HttpRequest(path = path), resolver) is Result.Success
            })
            mockFromJSON(body.jsonObject)
        else
            null
    }

    private fun matchTopLevelLog(
        pathLog: Value,
        urlMatchers: List<Pair<URLMatcher, Resolver>>,
        log: JSONObjectValue
    ): ScenarioStub? {
        val path = pathLog.toStringLiteral()
        return if (urlMatchers.any { (matcher, resolver) ->
                matcher.matches(HttpRequest(path = path), resolver) is Result.Success
            })
            mockFromJSON(log.jsonObject)
        else
            null
    }

    private fun findMatchingURLMatchers(feature: Feature): List<Pair<URLMatcher, Resolver>> {
        val urlMatchers: List<Pair<URLMatcher, Resolver>> = feature.scenarios.map {
            Pair(it.httpRequestPattern.urlMatcher, it.resolver)
        }.map { (matcher, resolver) ->
            Triple(matcher, matcher?.matches(URI.create(urlPathFilter)), resolver)
        }.filter {
            it.second is Result.Success
        }.map {
            Pair(it.first!!, it.third)
        }
        return urlMatchers
    }
}