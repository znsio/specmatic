package run.qontract.core

import io.cucumber.messages.Messages

class Suggestions(contractGherkinDocument: Messages.GherkinDocument) {
    val scenarios: List<Scenario> = lex(contractGherkinDocument)
    constructor(gherkinData: String) : this(parseGherkinString(gherkinData))
}