package io.specmatic.core

import io.specmatic.core.pattern.parsedJSONObject
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.StringValue
import io.specmatic.core.value.Value
import io.specmatic.mock.SUBSTITUTION_DATA
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SubstitutionTest {
    @Test
    fun `substitution using request body value`(){
        val request = HttpRequest("POST", "/data", body = parsedJSONObject("""{"id": "abc123"}"""))
        val responseValue = parsedJSONObject("""{"id": "{{REQUEST.BODY.id}}"}""")

        val updatedVaue = Substitution(request).resolveSubstitutions(responseValue) as JSONObjectValue

        assertThat(updatedVaue.findFirstChildByPath("id")?.toStringLiteral()).isEqualTo("abc123")
    }

    @Test
    fun `substitution using data`(){
        val request = HttpRequest("POST", "/data", body = parsedJSONObject("""{"department": "engineering"}"""))
        val responseValue = parsedJSONObject("""{"location": "{{@department}}"}""")

        val data = mapOf(
            "@department" to mapOf<String, Map<String, Value>>(
                "engineering" to mapOf(
                    "location" to StringValue("Dallas")
                )
            )
        )

        val updatedVaue = Substitution(request).resolveSubstitutions(responseValue) as JSONObjectValue

        assertThat(updatedVaue.findFirstChildByPath("location")?.toStringLiteral()).isEqualTo("Dallas")
    }
}