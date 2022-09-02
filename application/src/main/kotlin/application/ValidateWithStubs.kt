package application

import `in`.specmatic.conversions.OpenApiSpecification
import `in`.specmatic.core.HttpRequest
import `in`.specmatic.core.Resolver
import `in`.specmatic.core.Result
import `in`.specmatic.core.URLMatcher
import `in`.specmatic.core.log.logger
import `in`.specmatic.core.pattern.parsedJSONArray
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.mock.ScenarioStub
import `in`.specmatic.mock.mockFromJSON
import picocli.CommandLine
import java.io.File
import java.net.URI
import java.util.concurrent.Callable

@CommandLine.Command(name = "validate-with-stubs", mixinStandardHelpOptions = true)
class ValidateWithStubs : Callable<Unit> {
    @CommandLine.Parameters(index = "0", description = ["Contract path"])
    lateinit var contractPath: String

    @CommandLine.Parameters(index = "1", description = ["Log dir path"])
    lateinit var logDirPath: String

    @CommandLine.Parameters(index = "2", description = ["urlPathFilter"])
    lateinit var urlPathFilter: String

    override fun call() {
        val feature = OpenApiSpecification.fromFile(contractPath).toFeature()

        val urlMatchers: List<Pair<URLMatcher, Resolver>> = feature.scenarios.map {
            Pair(it.httpRequestPattern.urlMatcher, it.resolver)
        }.map { (matcher, resolver) ->
            Triple(matcher, matcher?.matches(URI.create(urlPathFilter)), resolver)
        }.filter {
            it.second is Result.Success
        }.map {
            Pair(it.first!!, it.third)
        }

        val stubList = parsedJSONArray(File("http-json.log").readText())

        val stubsMatchingURLs: Sequence<ScenarioStub> = stubList.list.asSequence().mapNotNull {
            val log = it as JSONObjectValue

            if (log.findFirstChildByName("contractMatched") == null)
                null
            else {
                val path = log.findFirstChildByPath("http-request.path")?.toStringLiteral()
                if (urlMatchers.any { (matcher, resolver) ->
                        matcher.matches(HttpRequest(path = path), resolver) is Result.Success
                    })
                    mockFromJSON(log.jsonObject)
                else
                    null
            }
        }

        val matchFailures: Sequence<ScenarioStub> = stubsMatchingURLs.filter {
            !feature.matches(it.request, it.response)
        }

        if(matchFailures.firstOrNull() == null)
            logger.log("The contract is compatible with all stubs")
        else {
            logger.log("The contract is not compatible with the following:")
            matchFailures.forEach {
                logger.log(it.toJSON().toStringLiteral())
                logger.newLine()
            }
        }
    }
}