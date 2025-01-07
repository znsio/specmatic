package io.specmatic.core.examples.server

import io.specmatic.core.MatchFailureDetails
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExampleValidationErrorMessageTest {

    @Test
    fun `should map single response JSON path to description`() {
        val matchFailureDetails = listOf(
            MatchFailureDetails(
                breadCrumbs = listOf("RESPONSE", "body", "person", "name"),
                errorMessages = listOf("Name is invalid"),
                isPartial = false
            )
        )
        val example = ExampleValidationErrorMessage(matchFailureDetails)
        val result = example.jsonPathToErrorDescriptionMapping()
        val expected = listOf(
            mapOf(
                "jsonPath" to "/http-response/body/person/name",
                "description" to ">> http-response.body.person.name\n\nName is invalid",
                "isPartial" to false
            )
        )

        assertEquals(expected, result)
    }

    @Test
    fun `should handle leading and trailing white space`() {
        val matchFailureDetails = listOf(
            MatchFailureDetails(
                breadCrumbs = listOf("RESPONSE", "body", "person", "name"),
                errorMessages = listOf(" Name is invalid "),
                isPartial = false
            )
        )
        val example = ExampleValidationErrorMessage(matchFailureDetails)
        val result = example.jsonPathToErrorDescriptionMapping()
        val expected = listOf(
            mapOf(
                "jsonPath" to "/http-response/body/person/name",
                "description" to ">> http-response.body.person.name\n\n Name is invalid ",
                "isPartial" to false
            )
        )

        assertEquals(expected, result)
    }

    @Test
    fun `should handle empty error description`() {
        val matchFailureDetails = listOf(
            MatchFailureDetails(
                breadCrumbs = listOf("RESPONSE", "body", "person", "name"),
                errorMessages = listOf(""),
                isPartial = false
            )
        )
        val example = ExampleValidationErrorMessage(matchFailureDetails)
        val result = example.jsonPathToErrorDescriptionMapping()
        val expected = listOf(
            mapOf(
                "jsonPath" to "/http-response/body/person/name",
                "description" to ">> http-response.body.person.name\n\n",
                "isPartial" to false
            )
        )

        assertEquals(expected, result)
    }

    @Test
    fun `should handle multiple errors with array indices`() {
        val matchFailureDetails = listOf(
            MatchFailureDetails(
                breadCrumbs = listOf("RESPONSE", "body", "items", "[0]", "name"),
                errorMessages = listOf("Name is missing"),
                isPartial = true
            ),
            MatchFailureDetails(
                breadCrumbs = listOf("RESPONSE", "body", "items", "[1]", "price"),
                errorMessages = listOf("Price is invalid"),
                isPartial = false
            )
        )
        val example = ExampleValidationErrorMessage(matchFailureDetails)
        val result = example.jsonPathToErrorDescriptionMapping()
        val expected = listOf(
            mapOf(
                "jsonPath" to "/http-response/body/items/0/name",
                "description" to ">> http-response.body.items.[0].name\n\nName is missing",
                "isPartial" to true
            ),
            mapOf(
                "jsonPath" to "/http-response/body/items/1/price",
                "description" to ">> http-response.body.items.[1].price\n\nPrice is invalid",
                "isPartial" to false
            )
        )

        assertEquals(expected, result)
    }

    @Test
    fun `should map single request JSON path to description`() {
        val matchFailureDetails = listOf(
            MatchFailureDetails(
                breadCrumbs = listOf("REQUEST", "body", "user", "age"),
                errorMessages = listOf("Age must be an integer"),
                isPartial = false
            )
        )
        val example = ExampleValidationErrorMessage(matchFailureDetails)
        val result = example.jsonPathToErrorDescriptionMapping()
        val expected = listOf(
            mapOf(
                "jsonPath" to "/http-request/body/user/age",
                "description" to ">> http-request.body.user.age\n\nAge must be an integer",
                "isPartial" to false
            )
        )

        assertEquals(expected, result)
    }

    @Test
    fun `should handle nested object errors`() {
        val matchFailureDetails = listOf(
            MatchFailureDetails(
                breadCrumbs = listOf("RESPONSE", "body", "order", "details", "shipping", "address"),
                errorMessages = listOf("Address is missing"),
                isPartial = true
            )
        )
        val example = ExampleValidationErrorMessage(matchFailureDetails)
        val result = example.jsonPathToErrorDescriptionMapping()
        val expected = listOf(
            mapOf(
                "jsonPath" to "/http-response/body/order/details/shipping/address",
                "description" to ">> http-response.body.order.details.shipping.address\n\nAddress is missing",
                "isPartial" to true
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
        val example = ExampleValidationErrorMessage(matchFailureDetails)
        val result = example.jsonPathToErrorDescriptionMapping()
        val expected = listOf(
            mapOf(
                "jsonPath" to "/http-response/query/id",
                "description" to ">> http-response.query.id\n\nID is invalid",
                "isPartial" to false
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
        val example = ExampleValidationErrorMessage(matchFailureDetails)
        val result = example.jsonPathToErrorDescriptionMapping()
        val expected = listOf(
            mapOf(
                "jsonPath" to "/http-response/headers/Content-Type",
                "description" to ">> http-response.headers.Content-Type\n\nContent-Type is missing",
                "isPartial" to true
            )
        )

        assertEquals(expected, result)
    }

    @Test
    fun `should handle empty match failure details`() {
        val example = ExampleValidationErrorMessage(emptyList())
        val result = example.jsonPathToErrorDescriptionMapping()
        val expected = emptyList<Map<String, Any>>()

        assertEquals(expected, result)
    }
}
