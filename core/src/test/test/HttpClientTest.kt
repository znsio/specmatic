package io.specmatic.test

import io.ktor.utils.io.charsets.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlin.text.Charsets

internal class HttpClientTest {
    @Test
    fun `should unzip a file successfully`() {
        val bytes = gzipEncode("hello world")

        assertThat(unzip(bytes, Charsets.UTF_8)).isEqualTo("hello world")
    }

    @Test
    fun `should unzip a file successfully, assuming UTF-8 if the charset is not supplied`() {
        val bytes = gzipEncode("hello world")

        assertThat(unzip(bytes, null)).isEqualTo("hello world")
    }

    private fun gzipEncode(data: String): ByteArray {
        val byteArrayOutputStream = ByteArrayOutputStream()
        GZIPOutputStream(byteArrayOutputStream).writer(Charsets.UTF_8).use { it.write(data) }

        byteArrayOutputStream.flush()
        val bytes = byteArrayOutputStream.toByteArray()
        return bytes
    }

    @Test
    fun `Content-Encoding header should be removed when decoding a response body`() {
        val bytes = gzipEncode("hello world")
        val (headers, body) = decodeBody(bytes, "gzip", Charset.forName("UTF-8"), mapOf("Content-Encoding" to "gzip"))

        assertThat(headers).isEmpty()
        assertThat(body).isEqualTo("hello world")
    }
}