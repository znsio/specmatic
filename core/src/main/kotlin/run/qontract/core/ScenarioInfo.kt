package run.qontract.core

import run.qontract.core.pattern.Pattern
import run.qontract.core.pattern.PatternTable
import java.util.HashMap

data class ScenarioInfo(
        var scenarioName: String = "",
        val httpRequestPattern: HttpRequestPattern = HttpRequestPattern(),
        val httpResponsePattern: HttpResponsePattern = HttpResponsePattern(),
        val expectedServerState: MutableMap<String, Any> = mutableMapOf(),
        val patterns: MutableMap<String, Pattern> = mutableMapOf(),
        val fixtures: MutableMap<String, Any> = mutableMapOf(),
        var examples: MutableList<PatternTable> = mutableListOf())