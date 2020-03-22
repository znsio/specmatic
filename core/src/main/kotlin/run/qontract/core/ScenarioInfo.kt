package run.qontract.core

import run.qontract.core.pattern.Pattern
import run.qontract.core.pattern.PatternTable

data class ScenarioInfo(
        val scenarioName: String = "",
        val httpRequestPattern: HttpRequestPattern = HttpRequestPattern(),
        val httpResponsePattern: HttpResponsePattern = HttpResponsePattern(),
        val expectedServerState: Map<String, Any> = emptyMap(),
        val patterns: Map<String, Pattern> = emptyMap(),
        val fixtures: Map<String, Any> = emptyMap(),
        val examples: List<PatternTable> = emptyList()
)