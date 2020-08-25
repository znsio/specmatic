package run.qontract.proxy

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatNoException
import org.junit.jupiter.api.Test
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestTemplate
import run.qontract.core.Feature
import run.qontract.stub.HttpStub
import java.io.File
import java.net.InetSocketAddress

internal class ProxyTest {
    @Test
    fun `basic test of the proxy`() {
        val simpleFeature = Feature("""
            Feature: Math
              Scenario: Square
                When POST /
                And request-body (number)
                Then status 200
                And response-body (number)
        """.trimIndent())

        val generatedContracts = File("./build/generatedContracts")
        if(generatedContracts.exists())
            generatedContracts.deleteRecursively()
        generatedContracts.mkdirs()

        HttpStub(simpleFeature).use {
            Proxy(host = "localhost", port = 9001, "", generatedContracts.path, null).use { proxy ->
                val restProxy = java.net.Proxy(java.net.Proxy.Type.HTTP, InetSocketAddress("localhost", 9001))
                val requestFactory = SimpleClientHttpRequestFactory()
                requestFactory.setProxy(restProxy)
                val client = RestTemplate(requestFactory)
                val response = client.postForEntity("http://localhost:9000/", "10", String::class.java)

                assertThat(response.statusCodeValue).isEqualTo(200)
                assertThatNoException().isThrownBy { response.body!!.toInt() }
            }
        }
    }

    @Test
    fun `basic test of the reverse proxy`() {
        val simpleFeature = Feature("""
            Feature: Math
              Scenario: Square
                When POST /
                And request-body (number)
                Then status 200
                And response-body (number)
        """.trimIndent())

        val generatedContracts = File("./build/generatedContracts")
        if(generatedContracts.exists())
            generatedContracts.deleteRecursively()
        generatedContracts.mkdirs()

        HttpStub(simpleFeature).use {
            Proxy(host = "localhost", port = 9001, "http://localhost:9000", generatedContracts.path, null).use { proxy ->
                val client = RestTemplate()
                val response = client.postForEntity("http://localhost:9001/", "10", String::class.java)

                assertThat(response.statusCodeValue).isEqualTo(200)
                assertThatNoException().isThrownBy { response.body!!.toInt() }
            }
        }
    }
}