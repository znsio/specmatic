package `in`.specmatic.core

import `in`.specmatic.core.pattern.Examples
import `in`.specmatic.core.pattern.KafkaMessagePattern
import `in`.specmatic.core.pattern.NullPattern
import `in`.specmatic.core.pattern.Pattern
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
    val async: Boolean = false,
    val channel: String = "",
    val payload: Pattern = NullPattern,
) {
    fun matchesSignature(other: ScenarioInfo) = httpRequestPattern.matchesSignature(other.httpRequestPattern) &&
            httpResponsePattern.status == other.httpResponsePattern.status
}
