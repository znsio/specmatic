package io.specmatic.core

import io.specmatic.core.pattern.parsedJSONObject
import io.specmatic.core.value.JSONObjectValue
import org.assertj.core.api.Assertions.assertThat

import org.junit.jupiter.api.Test

// NOTE: Concrete values are actual values, not placeholders like (string) or (number).
// For example, "ATYGHA" is a concrete value, not a placeholder.
class DictionaryTest {
    companion object {
        val dictionary = Dictionary(
            parsedJSONObject("""{"name": "John Doe", "address": "123 Main Street", "Authentication": "Bearer 123"}""").jsonObject
        )
    }

    @Test
    fun `should substitute concrete and non-concrete values when forceSubstitution is enabled`() {
        val httpResponse = HttpResponse(
            headers = mapOf("Authentication" to "RANDOM_STRING"),
            body = parsedJSONObject("""{"name": "RANDOM_STRING", "address": "RANDOM_STRING"}""")
        )
        val updatedResponse = httpResponse.substituteDictionaryValues(dictionary, forceSubstitution = true)
        val jsonResponse = updatedResponse.body as JSONObjectValue

        assertThat(updatedResponse.headers["Authentication"]).isEqualTo("Bearer 123")
        assertThat(jsonResponse.findFirstChildByPath("name")?.toStringLiteral()).isEqualTo("John Doe")
        assertThat(jsonResponse.findFirstChildByPath("address")?.toStringLiteral()).isEqualTo("123 Main Street")
    }

    @Test
    fun `should substitute only non-concrete values and retain concrete values when forceSubstitution is disabled`() {
        val httpRequest = HttpRequest(
            path = "/foo", method = "GET",
            headers = mapOf("Authentication" to "(string)"),
            body = parsedJSONObject("""{"name": "(string)", "address": "RANDOM_STRING"}""")
        )
        val updatedHttpRequest = httpRequest.substituteDictionaryValues(dictionary)
        val jsonResponse = updatedHttpRequest.body as JSONObjectValue

        assertThat(updatedHttpRequest.headers["Authentication"]).isEqualTo("Bearer 123")
        assertThat(jsonResponse.findFirstChildByPath("name")?.toStringLiteral()).isEqualTo("John Doe")
        assertThat(jsonResponse.findFirstChildByPath("address")?.toStringLiteral()).isEqualTo("RANDOM_STRING")
    }
}