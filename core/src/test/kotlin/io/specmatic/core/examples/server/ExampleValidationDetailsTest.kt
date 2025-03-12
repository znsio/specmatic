package io.specmatic.core.examples.server

import io.specmatic.core.MatchFailureDetails
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ExampleValidationDetailsTest {

    @ParameterizedTest
    @CsvSource(
        "RESPONSE-BODY-(~~~ ABC Object)-name, /http-response/body/name, http-response.body (when ABC Object).name",
        "RESPONSE-BODY-.(~~~ ABC Object)-name, /http-response/body/name, http-response.body (when ABC Object).name",
        "RESPONSE-BODY-(~~~ ABC)-details-.(~~~ XYZ)-name, /http-response/body/details/name, http-response.body (when ABC).details (when XYZ).name"
    )
    fun `should handle tilde breadcrumbs in JSON path and description`(breadCrumb: String, expectedJsonPath: String, expectedDescBreadCrumb: String) {
        val matchFailureDetails = listOf(
            MatchFailureDetails(
                breadCrumbs = breadCrumb.split("-"),
                errorMessages = listOf("Name is invalid"),
                isPartial = false
            )
        )
        val validationResults = ExampleValidationDetails(matchFailureDetails).jsonPathToErrorDescriptionMapping()
        val result = validationResults.single()


        assertThat(validationResults).hasSize(1)
        assertThat(result.jsonPath).isEqualTo(expectedJsonPath)
        assertThat(result.description).isEqualTo(">> $expectedDescBreadCrumb\n\nName is invalid")
        assertThat(result.severity).isEqualTo(Severity.ERROR)
    }

    @Test
    fun `should map single response JSON path to description`() {
        val matchFailureDetails = listOf(
            MatchFailureDetails(
                breadCrumbs = listOf("RESPONSE", "BODY", "person", "name"),
                errorMessages = listOf("Name is invalid"),
                isPartial = false
            )
        )
        val example = ExampleValidationDetails(matchFailureDetails)
        val result = example.jsonPathToErrorDescriptionMapping()
        val expected = listOf(
            ExampleValidationResult(
                jsonPath =  "/http-response/body/person/name",
                description =  ">> http-response.body.person.name\n\nName is invalid",
                severity =  Severity.ERROR
            )
        )

        assertEquals(expected, result)
    }

    @Test
    fun `should handle leading and trailing white space`() {
        val matchFailureDetails = listOf(
            MatchFailureDetails(
                breadCrumbs = listOf("RESPONSE", "BODY", "person", "name"),
                errorMessages = listOf(" Name is invalid "),
                isPartial = false
            )
        )
        val example = ExampleValidationDetails(matchFailureDetails)
        val result = example.jsonPathToErrorDescriptionMapping()
        val expected = listOf(
            ExampleValidationResult(
                jsonPath = "/http-response/body/person/name",
                description =  ">> http-response.body.person.name\n\n Name is invalid ",
                severity =  Severity.ERROR
            )
        )

        assertEquals(expected, result)
    }

    @Test
    fun `should handle empty error description`() {
        val matchFailureDetails = listOf(
            MatchFailureDetails(
                breadCrumbs = listOf("RESPONSE", "BODY", "person", "name"),
                errorMessages = listOf(""),
                isPartial = false
            )
        )
        val example = ExampleValidationDetails(matchFailureDetails)
        val result = example.jsonPathToErrorDescriptionMapping()
        val expected = listOf(
            ExampleValidationResult(
                jsonPath =  "/http-response/body/person/name",
                description =  ">> http-response.body.person.name\n\n",
                severity = Severity.ERROR
            )
        )

        assertEquals(expected, result)
    }

    @Test
    fun `should handle multiple errors with array indices`() {
        val matchFailureDetails = listOf(
            MatchFailureDetails(
                breadCrumbs = listOf("RESPONSE", "BODY", "items", "[0]", "name"),
                errorMessages = listOf("Name is missing"),
                isPartial = true
            ),
            MatchFailureDetails(
                breadCrumbs = listOf("RESPONSE", "BODY", "items", "[1]", "price"),
                errorMessages = listOf("Price is invalid"),
                isPartial = false
            )
        )
        val example = ExampleValidationDetails(matchFailureDetails)
        val result = example.jsonPathToErrorDescriptionMapping()
        val expected = listOf(
            ExampleValidationResult(
                jsonPath = "/http-response/body/items/1/price",
                description =  ">> http-response.body.items.[1].price\n\nPrice is invalid",
                severity =  Severity.ERROR
            ),
            ExampleValidationResult(
                jsonPath = "/http-response/body/items/0/name",
                description =  ">> http-response.body.items.[0].name\n\nName is missing",
                severity =  Severity.WARNING
            )
        )

        assertEquals(expected, result)
    }

    @Test
    fun `should map single request JSON path to description`() {
        val matchFailureDetails = listOf(
            MatchFailureDetails(
                breadCrumbs = listOf("REQUEST", "BODY", "user", "age"),
                errorMessages = listOf("Age must be an integer"),
                isPartial = false
            )
        )
        val example = ExampleValidationDetails(matchFailureDetails)
        val result = example.jsonPathToErrorDescriptionMapping()
        val expected = listOf(
            ExampleValidationResult(
                jsonPath =  "/http-request/body/user/age",
                description = ">> http-request.body.user.age\n\nAge must be an integer",
                severity =  Severity.ERROR
            )
        )

        assertEquals(expected, result)
    }

    @Test
    fun `should handle nested object errors`() {
        val matchFailureDetails = listOf(
            MatchFailureDetails(
                breadCrumbs = listOf("RESPONSE", "BODY", "order", "details", "shipping", "address"),
                errorMessages = listOf("Address is missing"),
                isPartial = true
            )
        )
        val example = ExampleValidationDetails(matchFailureDetails)
        val result = example.jsonPathToErrorDescriptionMapping()
        val expected = listOf(
            ExampleValidationResult(
                jsonPath =  "/http-response/body/order/details/shipping/address",
                description =  ">> http-response.body.order.details.shipping.address\n\nAddress is missing",
                severity =  Severity.WARNING
            )
        )

        assertEquals(expected, result)
    }

    @Test
    fun `should handle query parameter errors`() {
        val matchFailureDetails = listOf(
            MatchFailureDetails(
                breadCrumbs = listOf("RESPONSE", "QUERY-PARAMS", "id"),
                errorMessages = listOf("ID is invalid"),
                isPartial = false
            )
        )
        val example = ExampleValidationDetails(matchFailureDetails)
        val result = example.jsonPathToErrorDescriptionMapping()
        val expected = listOf(
            ExampleValidationResult(
                jsonPath =  "/http-response/query/id",
                description =  ">> http-response.query.id\n\nID is invalid",
                severity =  Severity.ERROR
            )
        )

        assertEquals(expected, result)
    }

    @Test
    fun `should handle header errors`() {
        val matchFailureDetails = listOf(
            MatchFailureDetails(
                breadCrumbs = listOf("RESPONSE", "HEADERS", "Content-Type"),
                errorMessages = listOf("Content-Type is missing"),
                isPartial = true
            )
        )
        val example = ExampleValidationDetails(matchFailureDetails)
        val result = example.jsonPathToErrorDescriptionMapping()
        val expected = listOf(
            ExampleValidationResult(
                jsonPath =  "/http-response/headers/Content-Type",
                description = ">> http-response.headers.Content-Type\n\nContent-Type is missing",
                severity =  Severity.WARNING
            )
        )

        assertEquals(expected, result)
    }

    @Test
    fun `should handle empty match failure details`() {
        val example = ExampleValidationDetails(emptyList())
        val result = example.jsonPathToErrorDescriptionMapping()
        val expected = emptyList<Map<String, Any>>()

        assertEquals(expected, result)
    }
}
