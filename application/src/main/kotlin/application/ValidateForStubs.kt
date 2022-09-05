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

        val requestLogs = parsedJSONArray(File(logDirPath).readText())

        val stubsMatchingURLs: List<Pair<ScenarioStub, ScenarioStub>> = requestLogs.list.mapNotNull {
            val log = it as JSONObjectValue

            when (val path = log.findFirstChildByPath("http-request.path")?.toStringLiteral()) {
                in listOf(
                    "/_specmatic/expectations",
                    "/_qontract/expectations"
                ) -> {
                    log.findFirstChildByPath("http-request.body.http-request.path")?.let { stubRequestPathLog ->
                        stubFromExpectationLog(log, stubRequestPathLog, urlMatchers)
                    }
                }
                null -> null
                else -> {
                    stubFromRequestLog(path, urlMatchers, log)?.let { Pair(mockFromJSON(log.jsonObject), it) }
                }
            }
        }

        val matchResults = stubsMatchingURLs.map { (container, matchingScenario) ->
            try {
                feature.matchingStub(matchingScenario.request, matchingScenario.response)
                Triple(Result.Success(), container, matchingScenario)
            } catch(e: NoMatchingScenario) {
                Triple(e.results.toResultIfAny(), container, matchingScenario)
            }
        }


        val matchFailures: List<Triple<Result, ScenarioStub, ScenarioStub>> = matchResults.filter {
            it.first is Result.Failure
        }

        val countOfSuccessfulMatches = matchResults.size - matchFailures.size

        if(matchFailures.isEmpty())
            logger.log("The contract is compatible with all stubs")
        else {
            logger.log("The contract is not compatible with the following:")
            matchFailures.forEach { (result, container, _) ->
                logger.log("--------------------")
                logger.log(container.toJSON().toStringLiteral())
                logger.log(result.reportString())
                logger.newLine()
            }
        }

        logger.newLine()
        logger.log("Matched ${matchResults.size}, Succeeded: $countOfSuccessfulMatches, Failed: ${matchFailures.size}")
        logger.newLine()
    }

    private fun stubFromExpectationLog(
        log: JSONObjectValue,
        stubRequestPathLog: Value,
        urlMatchers: List<Pair<URLMatcher, Resolver>>
    ): Pair<ScenarioStub, ScenarioStub>? {
        return if (log.findFirstChildByPath("http-response.status")?.toStringLiteral() == "200")
            stubFromExpectationLog(stubRequestPathLog, log, urlMatchers)?.let {
                Pair(
                    mockFromJSON(log.jsonObject),
                    it
                )
            }
        else
            null
    }

    private fun stubFromExpectationLog(
        stubRequestPathLog: Value,
        log: JSONObjectValue,
        urlMatchers: List<Pair<URLMatcher, Resolver>>
    ): ScenarioStub? {
        val path = stubRequestPathLog.toStringLiteral()

        val body = log.findFirstChildByPath("http-request.body") as JSONObjectValue

        if (urlMatchers.any { (matcher, resolver) ->
                matcher.matchesPath(path, resolver) is Result.Success
            })
            return mockFromJSON(body.jsonObject)

        return null
    }

    private fun stubFromRequestLog(
        path: String,
        urlMatchers: List<Pair<URLMatcher, Resolver>>,
        log: JSONObjectValue
    ): ScenarioStub? {
        if (urlMatchers.any { (matcher, resolver) ->
                matcher.matches(HttpRequest(path = path), resolver) is Result.Success
            })
            return mockFromJSON(log.jsonObject)

        return null
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