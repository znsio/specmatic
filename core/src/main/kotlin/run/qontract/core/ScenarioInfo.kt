package run.qontract.core

import run.qontract.core.pattern.Examples
import run.qontract.core.pattern.KafkaMessagePattern
import run.qontract.core.pattern.Pattern
import run.qontract.core.value.Value

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
        val setters: Map<String, String> = emptyMap()
)
