package run.qontract.core

import run.qontract.core.pattern.Pattern
import run.qontract.core.pattern.PatternTable
import run.qontract.core.value.Value

data class ScenarioInfo(
        val scenarioName: String = "",
        val httpRequestPattern: HttpRequestPattern = HttpRequestPattern(),
        val httpResponsePattern: HttpResponsePattern = HttpResponsePattern(),
        val expectedServerState: Map<String, Value> = emptyMap(),
        val patterns: Map<String, Pattern> = emptyMap(),
        val fixtures: Map<String, Value> = emptyMap(),
        val examples: List<PatternTable> = emptyList()
)