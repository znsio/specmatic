package run.qontract.core

import run.qontract.conversions.guessType
import run.qontract.core.GherkinSection.Then
import run.qontract.core.GherkinSection.When
import run.qontract.core.pattern.*
import run.qontract.core.value.*

data class GherkinClause(val content: String, val section: GherkinSection)

enum class GherkinSection(val prefix: String) {
    Given("Given"), When("When"), Then("Then"), `*`("*")
}

fun responseBodyToGherkinClauses(typeName: String, body: Value?): Pair<List<GherkinClause>, ExampleDeclaration>? {
    if(body == EmptyString)
        return Pair(emptyList(), ExampleDeclaration())

    return body?.typeDeclarationWithKey(typeName, ExampleDeclaration())?.let { (typeDeclaration, _) ->
        val bodyClause = GherkinClause("response-body ${typeDeclaration.typeValue}", Then)
        val typeDefinitionClauses = toGherkinClauses(typeDeclaration.types)

        Pair(listOf(bodyClause).plus(typeDefinitionClauses), ExampleDeclaration())
    }
}

fun requestBodyToGherkinClauses(body: Value?): Pair<List<GherkinClause>, ExampleDeclaration>? {
    if(body == EmptyString)
        return Pair(emptyList(), ExampleDeclaration())

    return body?.typeDeclarationWithoutKey("RequestBody", ExampleDeclaration())?.let { (typeDeclaration, exampleDeclaration) ->
        val typeValue = typeDeclaration.typeValue

        val bodyClause = GherkinClause("request-body $typeValue", When)
        val typeDefinitionClauses = toGherkinClauses(typeDeclaration.types)

        Pair(listOf(bodyClause).plus(typeDefinitionClauses), exampleDeclaration)
    }
}

fun toGherkinClauses(patterns: Map<String, Pattern>): List<GherkinClause> {
    return patterns.entries.map { (key, pattern) -> toClause(key, pattern) }
}

fun headersToGherkin(headers: Map<String, String>, keyword: String, exampleDeclaration: ExampleDeclaration, section: GherkinSection): Pair<List<GherkinClause>, ExampleDeclaration> {
    val (typeDeclarations, exampleDeclaration) = dictionaryToDeclarations(stringMapToValueMap(headers), exampleDeclaration)

    val returnedTypeClauses = typeDeclarationsToGherkin(typeDeclarations)

    val headerClauses = typeDeclarations.entries.map {
        "$keyword ${it.key} ${it.value.typeValue}"
    }.map { GherkinClause(it, section) }

    return Pair(returnedTypeClauses.plus(headerClauses), exampleDeclaration)
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

fun withFeatureClause(name: String, scenarios: String): String {
    return """Feature: $name
${scenarios.prependIndent("  ")}
"""
}

fun withScenarioClause(scenarioName: String, scenarioData: String): String {
    return """Scenario: $scenarioName
${scenarioData.prependIndent("  ")}
"""
}

fun toGherkinFeature(scenarioName: String, clauses: Pair<List<GherkinClause>, ExampleDeclaration>): String = withFeatureClause("New Feature", toGherkinScenario(scenarioName, clauses))

fun toGherkinScenario(scenarioName: String, declarations: Pair<List<GherkinClause>, ExampleDeclaration>): String {
    val (clauses, exampleDeclaration) = declarations
    val groupedClauses = clauses.groupBy { it.section }

    val statements = listOf(GherkinSection.Given, When, GherkinSection.Then, GherkinSection.`*`).flatMap { section ->
        val sectionClauses = groupedClauses[section] ?: emptyList()
        val prefixes = listOf(section.prefix).plus(1.until(sectionClauses.size).map { "And" })
        sectionClauses.zip(prefixes).map { (clause, prefix) -> GherkinStatement(clause.content, prefix) }
    }

    val statementString = statements.joinToString("\n") { it.toGherkinString() }

    val scenarioGherkin = when {
        exampleDeclaration.examples.isNotEmpty() -> {
            val entries = exampleDeclaration.examples.entries.toList()
            val heading = """| ${entries.joinToString(" | ") { it.key }} |"""
            val firstRow = """| ${entries.joinToString(" | ") { it.value }} |"""

            "$statementString\n\nExamples:\n$heading\n$firstRow"
        }
        else -> statementString
    }

    return withScenarioClause(scenarioName, scenarioGherkin)
}
