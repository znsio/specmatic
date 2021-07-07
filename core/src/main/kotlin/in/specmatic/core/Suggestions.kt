package `in`.specmatic.core

import `in`.specmatic.core.utilities.readFile
import io.cucumber.messages.types.GherkinDocument

class Suggestions(val scenarios: List<Scenario>) {
    constructor(contractGherkinDocument: GherkinDocument) : this(lex(contractGherkinDocument, "").second)

    companion object {
        fun fromFile(suggestionsPath: String): Suggestions {
            val suggestionsGherkin = readFile(suggestionsPath)
            return Suggestions(parseGherkinString(suggestionsGherkin, suggestionsPath))
        }
    }
}