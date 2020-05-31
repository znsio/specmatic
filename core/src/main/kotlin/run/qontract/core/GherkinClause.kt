package run.qontract.core

import run.qontract.conversions.guessType
import run.qontract.core.pattern.*
import run.qontract.core.value.Value

data class GherkinClause(val content: String, val section: GherkinSection)

enum class GherkinSection(val prefix: String) {
    Given("Given"), When("When"), Then("Then")
}

fun bodyToGherkinClauses(typeName: String, qontractKeyword: String, body: Value?, section: GherkinSection): List<GherkinClause>? =
        body?.typeDeclaration(typeName)?.let { typeDeclaration ->
            val responseBodyClause = GherkinClause("$qontractKeyword ${typeDeclaration.typeValue}", section)
            val typeDefinitionClauses = toGherkinClauses(typeDeclaration.types)

            listOf(responseBodyClause).plus(typeDefinitionClauses)
        }

fun toGherkinClauses(patterns: Map<String, Pattern>): List<GherkinClause> {
    return patterns.entries.map { (key, pattern) -> toClause(key, pattern) }
}

fun headersToGherkin(headers: Map<String, String>, keyword: String, section: GherkinSection): List<GherkinClause> {
    return headers.entries.map {
        "$keyword ${it.key} ${guessType(parsedValue(it.value)).type().pattern}"
    }.map { GherkinClause(it, section) }
}

fun toClause(key: String, type: Pattern): GherkinClause {
    val title = "type ${withoutPatternDelimiters(key)}"

    val table = when (type) {
        is TabularPattern -> patternMapToString(type.pattern)
        else -> "  | $key | ${type.pattern} |"
    }

    return GherkinClause("$title\n$table", GherkinSection.Given)
}

private fun patternMapToString(json: Map<String, Pattern>): String {
    return json.entries.joinToString("\n") {
        "  | ${it.key} | ${it.value.pattern} |"
    }
}

fun withFeatureClause(scenarios: String): String {
    return """Feature: New Feature
${scenarios.prependIndent("  ")}
"""
}

fun withScenarioClause(scenarioName: String, scenarioData: String): String {
    return """Scenario: $scenarioName
${scenarioData.prependIndent("  ")}
"""
}

fun toGherkinFeature(scenarioName: String, clauses: List<GherkinClause>): String = withFeatureClause(toGherkinScenario(scenarioName, clauses))

fun toGherkinScenario(scenarioName: String, clauses: List<GherkinClause>): String {
    val groupedClauses = clauses.groupBy { it.section }

    val statements = listOf(GherkinSection.Given, GherkinSection.When, GherkinSection.Then).flatMap { section ->
        val sectionClauses = groupedClauses[section] ?: emptyList()
        val prefixes = listOf(section.prefix).plus(1.until(sectionClauses.size).map { "And" })
        sectionClauses.zip(prefixes).map { (clause, prefix) -> GherkinStatement(clause.content, prefix) }
    }

    return withScenarioClause(scenarioName, statements.joinToString("\n") { it.toGherkinString() })
}
