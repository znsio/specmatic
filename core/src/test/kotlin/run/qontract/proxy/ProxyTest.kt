package run.qontract.proxy

import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestTemplate
import run.qontract.core.parseGherkinStringToFeature
import run.qontract.core.pattern.parsedJSON
import run.qontract.stub.HttpStub
import java.io.File
import java.net.InetSocketAddress

internal class ProxyTest {
    val simpleFeature = parseGherkinStringToFeature("""
            Feature: Math
              Scenario: Square
                When POST /
                And request-body (number)
                Then status 200
                And response-body 100
        """.trimIndent())

    @Test
    fun `basic test of the proxy`() {
        val generatedContracts = File("./build/generatedContracts")
        if(generatedContracts.exists())
            generatedContracts.deleteRecursively()
        generatedContracts.mkdirs()

        val flags = mutableListOf<String>()
        var receivedContract: String? = null
        var receivedStub: String? = null
        val receivedPaths = mutableListOf<String>()

        val fakeFileWriter = object : FileWriter {
            override fun createDirectory() {
                flags.add("createDirectory")
            }

            override fun writeText(path: String, content: String) {
                receivedPaths.add(path)

                if(path.endsWith(".qontract"))
                    receivedContract = content
                else
                    receivedStub = content
            }
        }

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

        assertThat(receivedContract?.trim()).startsWith("Feature:")
        assertThatCode { parseGherkinStringToFeature(receivedContract ?: "") }.doesNotThrowAnyException()
        assertThatCode { parsedJSON(receivedStub ?: "") }.doesNotThrowAnyException()
        assertThat(receivedPaths.toList()).isEqualTo(listOf("proxy_generated.qontract", "stub0.json"))
    }

    @Test
    fun `basic test of the reverse proxy`() {
        val generatedContracts = File("./build/generatedContracts")
        if(generatedContracts.exists())
            generatedContracts.deleteRecursively()
        generatedContracts.mkdirs()

        val flags = mutableListOf<String>()
        var receivedContract: String? = null
        var receivedStub: String? = null
        val receivedPaths = mutableListOf<String>()

        val fakeFileWriter = object : FileWriter {
            override fun createDirectory() {
                flags.add("createDirectory")
            }

            override fun writeText(path: String, content: String) {
                receivedPaths.add(path)

                if(path.endsWith(".qontract"))
                    receivedContract = content
                else
                    receivedStub = content
            }
        }

        HttpStub(simpleFeature).use {
            Proxy(host = "localhost", port = 9001, "http://localhost:9000", fakeFileWriter).use {
                val client = RestTemplate()
                val response = client.postForEntity("http://localhost:9001/", "10", String::class.java)

                assertThat(response.statusCodeValue).isEqualTo(200)
                assertThatNoException().isThrownBy { response.body!!.toInt() }
            }
        }

        assertThat(receivedContract?.trim()).startsWith("Feature:")
        assertThatCode { parseGherkinStringToFeature(receivedContract ?: "") }.doesNotThrowAnyException()
        assertThatCode { parsedJSON(receivedStub ?: "") }.doesNotThrowAnyException()
        assertThat(receivedPaths.toList()).isEqualTo(listOf("proxy_generated.qontract", "stub0.json"))
    }
}
