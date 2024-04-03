package `in`.specmatic.test

import `in`.specmatic.core.HttpRequest
import io.ktor.client.plugins.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.charsets.*
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlin.text.Charsets

internal class HttpClientTest {
    @Test
    fun `should unzip a file successfully`() {
        val bytes = gzipEncode()

        Assertions.assertThat(unzip(bytes, Charsets.UTF_8)).isEqualTo("hello world")
    }

    @Test
    fun `should unzip a file successfully, assuming UTF-8 if the charset is not supplied`() {
        val bytes = gzipEncode()

        Assertions.assertThat(unzip(bytes, null)).isEqualTo("hello world")
    }

    private fun gzipEncode(): ByteArray {
        val byteArrayOutputStream = ByteArrayOutputStream()
        GZIPOutputStream(byteArrayOutputStream).writer(Charsets.UTF_8).use { it.write("hello world") }

        byteArrayOutputStream.flush()
        return byteArrayOutputStream.toByteArray()
    }

    @Test
    fun `Content-Encoding header should be removed when decoding a response body`() {
        val bytes = gzipEncode()
        val (headers, body) = decodeBody(bytes, "gzip", Charset.forName("UTF-8"), mapOf("Content-Encoding" to "gzip"))

        Assertions.assertThat(headers).isEmpty()
        Assertions.assertThat(body).isEqualTo("hello world")
    }

    @Test
    fun `Request should timeout after given timeout argument`() {

        val server = embeddedServer(Netty, port = 8080) {
            routing {
                get("/data") {
                    Thread.sleep(3000)
                    call.respondText("Hello, world!")
                }
            }
        }

        server.start();

        Assertions.assertThatThrownBy {
            val request = HttpRequest("GET", "/data")
            HttpClient("http://localhost:8080", timeout = 1).execute(request)
        }.isInstanceOf(HttpRequestTimeoutException::class.java)

        Thread.sleep(2000)
        server.stop()
    }
}