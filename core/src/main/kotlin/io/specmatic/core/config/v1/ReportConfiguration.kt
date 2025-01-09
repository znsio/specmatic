package io.specmatic.core.config.v1

import com.fasterxml.jackson.annotation.JsonProperty

data class ReportConfiguration(
    val formatters: List<ReportFormatter>? = null,
    val types: ReportTypes = ReportTypes()
)

data class ReportFormatter(
    var type: ReportFormatterType = ReportFormatterType.TEXT,
    val layout: ReportFormatterLayout = ReportFormatterLayout.TABLE,
    val lite: Boolean = false,
    val title: String = "Specmatic Report",
    val logo: String = "assets/specmatic-logo.svg",
    val logoAltText: String = "Specmatic",
    val heading: String = "Contract Test Results",
    val outputDirectory: String = "./build/reports/specmatic/html"
)

enum class ReportFormatterType {
    @JsonProperty("text")
    TEXT,

    @JsonProperty("html")
    HTML
}

enum class ReportFormatterLayout {
    @JsonProperty("table")
    TABLE
}

data class ReportTypes (
    @JsonProperty("APICoverage")
    val apiCoverage: APICoverage = APICoverage()
)

data class APICoverage (
    @JsonProperty("OpenAPI")
    val openAPI: APICoverageConfiguration = APICoverageConfiguration()
)