package `in`.specmatic.core

class FailureReport(val contractPath: String?, val scenarioMessage: String?, val scenario: ScenarioDetailsForResult?, val matchFailureDetailList: List<MatchFailureDetails>): Report {
    override fun toText(): String {
        val contractLine = contractPathDetails()
        val scenarioDetails = scenarioDetails(scenario) ?: ""

        val matchFailureDetails = matchFailureDetails()

        val reportDetails: String = scenario?.let {
            "$scenarioDetails${System.lineSeparator()}${System.lineSeparator()}${matchFailureDetails.prependIndent("  ")}"
        } ?: matchFailureDetails

        val report = contractLine?.let {
            val reportIndent = if(contractLine.isNotEmpty()) "  " else ""
            "$contractLine${reportDetails.prependIndent(reportIndent)}"
        } ?: reportDetails

        return report.trim()
    }

    override fun toString(): String = toText()

    private fun matchFailureDetails(): String {
        return matchFailureDetailList.joinToString("\n\n") {
            matchFailureDetails(it)
        }
    }

    private fun matchFailureDetails(matchFailureDetails: MatchFailureDetails): String {
        return matchFailureDetails.let { (breadCrumbs, errorMessages) ->
            val breadCrumbString = breadCrumbString(breadCrumbs)

            val errorMessagesString = errorMessages.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")

            "$breadCrumbString${System.lineSeparator()}${System.lineSeparator()}${errorMessagesString.prependIndent("   ")}".trim()
        }
    }

    private fun breadCrumbString(breadCrumbs: List<String>): String {
        return breadCrumbs
            .filter { it.isNotBlank() }
            .joinToString(".") { it.trim() }
            .replace(".(~~~ ", " (when ")
            .replace(Regex("^\\(~~~ "), "(when ")
            .let {
                when {
                    it.isNotBlank() -> ">> $it"
                    else -> ""
                }
            }.replace(".[", "[")
    }

    private fun contractPathDetails(): String? {
        if(contractPath == null || contractPath.isBlank())
            return null

        return "Error from contract $contractPath\n\n"
    }

    private fun scenarioDetails(scenario: ScenarioDetailsForResult?): String? {
        return scenario?.let {
            val scenarioLine = """${scenarioMessage ?: "In scenario"} "${scenario.name}""""
            val urlLine =
                "API: ${scenario.method} ${scenario.path} -> ${scenario.status}"

            "$scenarioLine${System.lineSeparator()}$urlLine"
        }
    }
}