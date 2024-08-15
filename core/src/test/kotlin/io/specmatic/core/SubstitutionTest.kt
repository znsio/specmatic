package io.specmatic.core

import io.specmatic.core.pattern.parsedJSONObject
import io.specmatic.core.value.JSONObjectValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class SubstitutionTest {
    @Test
    @Disabled
    fun `substitution using request body value`(){
        val request = HttpRequest("POST", "/data", body = parsedJSONObject("""{"id": "abc123"}"""))
        val responseValue = parsedJSONObject("""{"id": "{{REQUEST.BODY.id}}"}""")

//        val updatedVaue = Substitution(request, httpPathPattern, headersPattern, httpQueryParamPattern, body, resolver).resolveSubstitutions(responseValue) as JSONObjectValue
//
//        assertThat(updatedVaue.findFirstChildByPath("id")?.toStringLiteral()).isEqualTo("abc123")
    }
}