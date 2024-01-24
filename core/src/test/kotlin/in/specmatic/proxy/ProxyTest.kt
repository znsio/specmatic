package `in`.specmatic.proxy

import `in`.specmatic.conversions.OpenApiSpecification
import `in`.specmatic.core.YAML
import `in`.specmatic.core.parseGherkinStringToFeature
import `in`.specmatic.core.pattern.parsedJSON
import `in`.specmatic.stub.HttpStub
import io.ktor.http.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestTemplate
import java.io.File
import java.net.InetSocketAddress

internal class ProxyTest {
    private val dynamicHttpHeaders = listOf(
        HttpHeaders.Authorization,
        HttpHeaders.UserAgent,
        HttpHeaders.Cookie,
        HttpHeaders.Referrer,
        HttpHeaders.AcceptLanguage,
        HttpHeaders.Host,
        HttpHeaders.IfModifiedSince,
        HttpHeaders.IfNoneMatch,
        HttpHeaders.CacheControl,
        HttpHeaders.ContentLength,
        HttpHeaders.Range,
        HttpHeaders.XForwardedFor
    )

    private val simpleFeature = parseGherkinStringToFeature(
        """
            Feature: Math
              Scenario: Square
                When POST /
                And request-body (number)
                Then status 200
                And response-body 100
        """.trimIndent()
    )

    private var fakeFileWriter: FakeFileWriter = FakeFileWriter()
    private val generatedContracts = File("./build/generatedContracts")

    @BeforeEach
    fun setup() {
        if (generatedContracts.exists())
            generatedContracts.deleteRecursively()
        generatedContracts.mkdirs()
    }

    @Test
    fun `basic test of the proxy`() {
        HttpStub(simpleFeature).use {
            Proxy(host = "localhost", port = 9001, "", fakeFileWriter).use {
                val restProxy = java.net.Proxy(java.net.Proxy.Type.HTTP, InetSocketAddress("localhost", 9001))
                val requestFactory = SimpleClientHttpRequestFactory()
                requestFactory.setProxy(restProxy)
                val client = RestTemplate(requestFactory)
                val response = client.postForEntity("http://localhost:9000/", "10", String::class.java)

                assertThat(response.statusCodeValue).isEqualTo(200)
                assertThatNoException().isThrownBy { response.body!!.toInt() }
            }
        }

        assertThat(fakeFileWriter.receivedContract?.trim()).startsWith("openapi:")
        assertThatCode {
            OpenApiSpecification.fromYAML(
                fakeFileWriter.receivedContract!!,
                ""
            )
        }.doesNotThrowAnyException()
        assertThatCode { parsedJSON(fakeFileWriter.receivedStub ?: "") }.doesNotThrowAnyException()
        assertThat(fakeFileWriter.receivedPaths.toList()).isEqualTo(listOf("proxy_generated.yaml", "stub0.json"))
    }

    @Test
    fun `basic test of the reverse proxy`() {
        HttpStub(simpleFeature).use {
            Proxy(host = "localhost", port = 9001, "http://localhost:9000", fakeFileWriter).use {
                val client = RestTemplate()
                val response = client.postForEntity("http://localhost:9001/", "10", String::class.java)

                assertThat(response.statusCodeValue).isEqualTo(200)
                assertThatNoException().isThrownBy { response.body!!.toInt() }
            }
        }

        assertThat(fakeFileWriter.receivedContract?.trim()).startsWith("openapi:")
        assertThatCode {
            OpenApiSpecification.fromYAML(
                fakeFileWriter.receivedContract!!,
                ""
            )
        }.doesNotThrowAnyException()
        assertThatCode { parsedJSON(fakeFileWriter.receivedStub ?: "") }.doesNotThrowAnyException()
        assertThat(fakeFileWriter.receivedPaths).isEqualTo(listOf("proxy_generated.yaml", "stub0.json"))
    }

    @Test
    fun `reverse proxy should record a space appropriately`() {
        val spec = OpenApiSpecification.fromYAML("""
            openapi: 3.0.1
            info:
              title: Data
              version: "1"
            paths:
              /da ta:
                get:
                  summary: Data
                  responses:
                    "200":
                      description: Data
                      content:
                        text/plain:
                          schema:
                            type: string
        """.trimIndent(), "").toFeature()

        HttpStub(spec).use {
            Proxy(host = "localhost", port = 9001, "http://localhost:9000", fakeFileWriter).use {
                val client = RestTemplate()
                val response = client.getForEntity("http://localhost:9001/da ta", String::class.java)
            }
        }

        assertThat(fakeFileWriter.receivedContract?.trim()).startsWith("openapi:")
        assertThat(fakeFileWriter.receivedContract!!).contains("/da ta")
    }

    @Test
    fun `should not include standard http headers in the generated specification`() {
        HttpStub(simpleFeature).use {
            Proxy(host = "localhost", port = 9001, "http://localhost:9000", fakeFileWriter).use {
                val client = RestTemplate()
                val response = client.postForEntity("http://localhost:9001/", "10", String::class.java)

                assertThat(response.statusCodeValue).isEqualTo(200)
                assertThatNoException().isThrownBy { response.body!!.toInt() }
            }
        }

        assertThat(fakeFileWriter.receivedContract?.trim()).startsWith("openapi:")

        dynamicHttpHeaders.forEach {
            assertThat(fakeFileWriter.receivedContract).withFailMessage("Specification should not have contained $it")
                .doesNotContainIgnoringCase("name: $it")
            assertThat(fakeFileWriter.receivedStub).withFailMessage("Stub should not have contained $it")
        }
    }
}

class FakeFileWriter : FileWriter {
    var receivedContract: String? = null
    var receivedStub: String? = null
    val flags = mutableListOf<String>()
    val receivedPaths = mutableListOf<String>()

    override fun createDirectory() {
        this.flags.add("createDirectory")
    }

    override fun writeText(path: String, content: String) {
        this.receivedPaths.add(path)

        if (path.endsWith(".${YAML}"))
            this.receivedContract = content
        else
            this.receivedStub = content
    }

    override fun subDirectory(path: String): FileWriter {
        return this
    }
}