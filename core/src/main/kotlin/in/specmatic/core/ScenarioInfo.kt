package `in`.specmatic.core

import `in`.specmatic.conversions.ApiSpecification
import `in`.specmatic.core.pattern.*
import `in`.specmatic.core.value.Value

data class ScenarioInfo(
    val scenarioName: String = "",
    val httpRequestPattern: HttpRequestPattern = HttpRequestPattern(),
    val httpResponsePattern: HttpResponsePattern = HttpResponsePattern(),
    val expectedServerState: Map<String, Value> = emptyMap(),
    val patterns: Map<String, Pattern> = emptyMap(),
    val fixtures: Map<String, Value> = emptyMap(),
    val examples: List<Examples> = emptyList(),
    val kafkaMessage: KafkaMessagePattern? = null,
    val ignoreFailure: Boolean = false,
    val references: Map<String, References> = emptyMap(),
    val bindings: Map<String, String> = emptyMap(),
    val isGherkinScenario: Boolean = false
) {
    fun matchesSignature(other: ScenarioInfo) = httpRequestPattern.matchesSignature(other.httpRequestPattern) &&
            httpResponsePattern.status == other.httpResponsePattern.status

    fun matchesGherkinWrapperPath(scenarioInfos: List<ScenarioInfo>, apiSpecification: ApiSpecification): List<ScenarioInfo> =
        scenarioInfos.filter { openApiScenarioInfo ->
            val pathPatternFromOpenApi = openApiScenarioInfo.httpRequestPattern.urlMatcher!!.pathPattern
            val pathPatternFromWrapper = this.httpRequestPattern.urlMatcher!!.pathPattern

            if(pathPatternFromOpenApi.size != pathPatternFromWrapper.size)
                return@filter false

            val resolver = Resolver(newPatterns = openApiScenarioInfo.patterns)
            val zipped = pathPatternFromOpenApi.zip(pathPatternFromWrapper)

            zipped.all { (openapiURLPart: URLPathPattern, wrapperURLPart: URLPathPattern) ->
                val openapiType = if(openapiURLPart.pattern is ExactValuePattern) "exact" else "pattern"
                val wrapperType = if(wrapperURLPart.pattern is ExactValuePattern) "exact" else "pattern"

                when(Pair(openapiType, wrapperType)) {
                    Pair("exact", "exact") -> apiSpecification.exactValuePatternsAreEqual(openapiURLPart, wrapperURLPart)
                    Pair("exact", "pattern") -> false
                    Pair("pattern", "exact") -> {
                        attempt("Error matching url ${this.httpRequestPattern.urlMatcher.path} to the specification") {
                            apiSpecification.patternMatchesExact(
                                wrapperURLPart,
                                openapiURLPart,
                                resolver
                            )
                        }
                    }
                    Pair("pattern", "pattern") -> {
                        val valueFromOpenapi = openapiURLPart.pattern.generate(Resolver(newPatterns = openApiScenarioInfo.patterns))
                        val valueFromWrapper = wrapperURLPart.pattern.generate(Resolver(newPatterns = this.patterns))

                        valueFromOpenapi.javaClass == valueFromWrapper.javaClass
                    }
                    else -> false
                }
            }
        }
}
