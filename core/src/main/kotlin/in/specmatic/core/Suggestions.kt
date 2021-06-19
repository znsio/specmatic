package `in`.specmatic.core

import io.cucumber.messages.types.GherkinDocument

class Suggestions(contractGherkinDocument: GherkinDocument) {
    val scenarios: List<Scenario> = lex(contractGherkinDocument, "").second
    constructor(gherkinData: String) : this(parseGherkinString(gherkinData))
}