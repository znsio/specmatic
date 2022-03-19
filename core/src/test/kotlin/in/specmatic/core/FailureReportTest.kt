package `in`.specmatic.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class FailureReportTest {
    @Test
    fun `breadcrumbs should be flush left and descriptions indented`() {
        val personIdDetails = MatchFailureDetails(listOf("person", "id"), listOf("error"))
        val report = FailureReport(null, null, null, listOf(personIdDetails))

        assertThat(report.toText()).isEqualTo("""
            >> person.id

               error
        """.trimIndent())
    }

    @Test
    fun `with multiple errors all breadcrumbs should be flush left and all descriptions indented`() {
        val personIdDetails = MatchFailureDetails(listOf("person", "id"), listOf("error"))
        val personNameDetails = MatchFailureDetails(listOf("person", "name"), listOf("error"))

        val report = FailureReport(null, null, null, listOf(personIdDetails, personNameDetails))

        assertThat(report.toText()).isEqualTo("""
            >> person.id

               error

            >> person.name

               error
        """.trimIndent())
    }
}