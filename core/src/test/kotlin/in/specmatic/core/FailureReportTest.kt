package `in`.specmatic.core

import `in`.specmatic.trimmedLinesString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class FailureReportTest {
    @Test
    fun `breadcrumbs should be flush left and descriptions indented`() {
        val personIdDetails = MatchFailureDetails(listOf("person", "id"), listOf("error"))
        val report = FailureReport(null, null, null, listOf(personIdDetails))

        assertThat(report.toText().trimmedLinesString()).isEqualTo("""
            >> person.id

               error
        """.trimIndent().trimmedLinesString())
    }

    @Test
    fun `with multiple errors all breadcrumbs should be flush left and all descriptions indented`() {
        val personIdDetails = MatchFailureDetails(listOf("person", "id"), listOf("error"))
        val personNameDetails = MatchFailureDetails(listOf("person", "name"), listOf("error"))

        val report = FailureReport(null, null, null, listOf(personIdDetails, personNameDetails))

        assertThat(report.toText().trimmedLinesString()).isEqualTo("""
            >> person.id

               error

            >> person.name

               error
        """.trimIndent().trimmedLinesString())
    }

    @Test
    fun `breadcrumb path segments should be separated from each other by a dot`() {
        val errorDetails = MatchFailureDetails(listOf("address", "street"), listOf("error"))

        val report = FailureReport(null, null, null, listOf(errorDetails))

        val reportText = report.toText()

        assertThat(reportText).contains("address.street")
    }

    @Test
    fun `array breadcrumbs should not be separated from previous breadcrumb by a dot`() {
        val errorDetails = MatchFailureDetails(listOf("addresses", "[0]", "street"), listOf("error"))

        val report = FailureReport(null, null, null, listOf(errorDetails))

        val reportText = report.toText()
        println(reportText)

        assertThat(reportText).contains("addresses[0].street")
    }

    @Test
    fun `should return error message containing all the errors if there are multiple error causes`() {
        val matchFailureDetailList = listOf(
            MatchFailureDetails(errorMessages =  listOf("first error message")),
            MatchFailureDetails(errorMessages = listOf("second error message"))
        )
        val failureReport = FailureReport(
            contractPath = null,
            scenarioMessage = null,
            scenario = null,
            matchFailureDetailList =  matchFailureDetailList
        )

        val expectedErrorMessage = """
            first error message

            second error message
        """.trimIndent()
        assertThat(failureReport.errorMessage()).isEqualTo(expectedErrorMessage)
    }

    @Test
    fun `should return error message containing single error if there is single error cause`() {
        val matchFailureDetailList = listOf(
            MatchFailureDetails(errorMessages =  listOf("error message")),
        )
        val failureReport = FailureReport(
            contractPath = null,
            scenarioMessage = null,
            scenario = null,
            matchFailureDetailList =  matchFailureDetailList
        )

        val expectedErrorMessage = """
            error message
        """.trimIndent()
        assertThat(failureReport.errorMessage()).isEqualTo(expectedErrorMessage)
    }
}