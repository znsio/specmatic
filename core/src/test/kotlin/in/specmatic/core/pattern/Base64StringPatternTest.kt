package `in`.specmatic.core.pattern

import `in`.specmatic.core.Resolver
import `in`.specmatic.core.value.NullValue
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.shouldMatch
import `in`.specmatic.shouldNotMatch
import org.apache.commons.codec.binary.Base64
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class Base64StringPatternTest {

    @Test
    fun `should fail to match null values gracefully`() {
        NullValue shouldNotMatch Base64StringPattern()
    }

    @Test
    fun `should match base 64 string value`() {
        StringValue("U3AzY200dDFrIFJ1bEV6") shouldMatch Base64StringPattern()
    }

    @Test
    fun `should fail to match non base 64 string value`() {
        StringValue("tH1sIsN0tB64^") shouldNotMatch Base64StringPattern()
    }

    @Test
    fun `should generate base64 random string`() {
        assertThat(Base64.isBase64(Base64StringPattern().generate(Resolver()).toStringLiteral()))
    }

    @Test
    fun `should match empty String`() {
        assertThat(Base64StringPattern().matches(StringValue(""), Resolver()).isSuccess()).isTrue
    }



}