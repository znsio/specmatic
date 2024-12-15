package io.specmatic.core.examples.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExampleValidationErrorMessageTest {

    @Test
    fun `should map single response JSON path to description`() {
        val errorMessage = ">> RESPONSE.body.person.name\n Name is invalid"
        val example = ExampleValidationErrorMessage(errorMessage)

        val result = example.jsonPathToErrorDescriptionMapping()
        val expected = listOf(
            mapOf(
                "jsonPath" to "/http-response/body/person/name",
                "description" to ">> RESPONSE.body.person.name\n Name is invalid"
            )
        )

        assertEquals(expected, result)
    }

    @Test
    fun `should handle multiple errors with array indices`() {
        val errorMessage =
            ">> RESPONSE.body.items[0].name\n Name is missing\n >> RESPONSE.body.items[1].price\n Price is invalid"
        val example = ExampleValidationErrorMessage(errorMessage)

        val result = example.jsonPathToErrorDescriptionMapping()
        val expected = listOf(
            mapOf(
                "jsonPath" to "/http-response/body/items/0/name",
                "description" to ">> RESPONSE.body.items[0].name\n Name is missing"
            ),
            mapOf(
                "jsonPath" to "/http-response/body/items/1/price",
                "description" to ">> RESPONSE.body.items[1].price\n Price is invalid"
            )
        )

        assertEquals(expected, result)
    }

    @Test
    fun `should map single request JSON path to description`() {
        val errorMessage = ">> REQUEST.body.user.age\n Age must be an integer"
        val example = ExampleValidationErrorMessage(errorMessage)

        val result = example.jsonPathToErrorDescriptionMapping()
        val expected = listOf(
            mapOf(
                "jsonPath" to "/http-request/body/user/age",
                "description" to ">> REQUEST.body.user.age\n Age must be an integer"
            )
        )

        assertEquals(expected, result)
    }

    @Test
    fun `should handle errors with nested objects`() {
        val errorMessage = ">> RESPONSE.body.order.details.shipping.address \n Address is missing"
        val example = ExampleValidationErrorMessage(errorMessage)

        val result = example.jsonPathToErrorDescriptionMapping()
        val expected = listOf(
            mapOf(
                "jsonPath" to "/http-response/body/order/details/shipping/address",
                "description" to ">> RESPONSE.body.order.details.shipping.address \n Address is missing"
            )
        )

        assertEquals(expected, result)
    }

    @Test
    fun `should handle empty error message`() {
        val errorMessage = ""
        val example = ExampleValidationErrorMessage(errorMessage)
        val result = example.jsonPathToErrorDescriptionMapping()

        val expected = emptyList<Map<String, String>>()

        assertEquals(expected, result)
    }


    @Test
    fun `should replace array indices correctly`() {
        val errorMessage = ">> RESPONSE.body.items[10].name\n Name is missing"
        val example = ExampleValidationErrorMessage(errorMessage)

        val result = example.jsonPathToErrorDescriptionMapping()

        val expected = listOf(
            mapOf(
                "jsonPath" to "/http-response/body/items/10/name",
                "description" to ">> RESPONSE.body.items[10].name\n Name is missing"
            )
        )

        assertEquals(expected, result)
    }

    @Test
    fun `should handle leading and trailing whitespace in error message`() {
        val errorMessage = "   >> RESPONSE.body.user.name\n Name is invalid   "
        val example = ExampleValidationErrorMessage(errorMessage)

        val result = example.jsonPathToErrorDescriptionMapping()

        val expected = listOf(
            mapOf(
                "jsonPath" to "/http-response/body/user/name",
                "description" to ">> RESPONSE.body.user.name\n Name is invalid"
            )
        )

        assertEquals(expected, result)
    }

    @Test
    fun `should handle invalid breadcrumb format`() {
        val errorMessage = ">>REQUEST.body.any\n Invalid Path"
        val example = ExampleValidationErrorMessage(errorMessage)

        val result = example.jsonPathToErrorDescriptionMapping()
        val expected = emptyList<Map<String, String>>()

        assertEquals(expected, result)
    }

}
