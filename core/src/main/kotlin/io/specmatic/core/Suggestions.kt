package io.specmatic.core

import io.cucumber.messages.types.GherkinDocument
import io.specmatic.core.utilities.readFile

class Suggestions(val scenarios: List<Scenario>) {
    constructor(contractGherkinDocument: GherkinDocument) : this(lex(contractGherkinDocument, "").second)

    companion object {
        fun fromFile(suggestionsPath: String): Suggestions {
            val suggestionsGherkin = readFile(suggestionsPath)
            return Suggestions(parseGherkinString(suggestionsGherkin, suggestionsPath))
        }
    }
}