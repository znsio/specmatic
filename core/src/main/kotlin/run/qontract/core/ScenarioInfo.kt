package run.qontract.core

import run.qontract.core.pattern.Pattern
import run.qontract.core.pattern.PatternTable
import java.util.HashMap

data class ScenarioInfo(
        var scenarioName: String = "",
        val httpRequestPattern: HttpRequestPattern = HttpRequestPattern(),
        val httpResponsePattern: HttpResponsePattern = HttpResponsePattern(),
        val expectedServerState: HashMap<String, Any> = HashMap(),
        val patterns: HashMap<String, Pattern> = HashMap(),
        val fixtures: HashMap<String, Any> = HashMap(),
        var examples: MutableList<PatternTable> = mutableListOf()) {

    fun deepCopy(): ScenarioInfo {
        return ScenarioInfo(
                scenarioName,
                httpRequestPattern.deepCopy(),
                httpResponsePattern.deepCopy(),
                HashMap<String, Any>().also { it.putAll(expectedServerState) },
                HashMap<String, Pattern>().also { it.putAll(patterns) },
                HashMap<String, Any>().also { it.putAll(fixtures) },
                examples.map { it }.toMutableList()
        )
    }
}