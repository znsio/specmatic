package run.qontract.proxy

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatNoException
import org.junit.jupiter.api.Test
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestTemplate
import run.qontract.core.Feature
import run.qontract.core.pattern.parsedJSONStructure
import run.qontract.stub.HttpStub
import java.io.File
import java.net.InetSocketAddress

internal class ProxyTest {
    val simpleFeature = Feature("""
            Feature: Math
              Scenario: Square
                When POST /
                And request-body (number)
                Then status 200
                And response-body 100
        """.trimIndent())

    @Test
    fun `basic test of the proxy`() {
        val generatedContract = """
            Feature: New feature
  Scenario: POST http://localhost:9000/
    When POST http://localhost:9000/
    And request-header Accept (string)
    And request-header Content-Type (string)
    And request-header User-Agent (string)
    And request-header Host (string)
    And request-header Proxy-Connection (string)
    And request-header Content-Length (number)
    And request-body (RequestBody: string)
    Then status 200
    And response-header Connection (string)
    And response-body (number)
  
    Examples:
    | Accept | Content-Type | User-Agent | Host | Proxy-Connection | Content-Length | RequestBody |
    | text/plain, application/json, application/*+json, */* | text/plain;charset=ISO-8859-1 | Java/1.8.0_241 | localhost:9000 | keep-alive | 2 | 10 |
        """.trim()

        val generatedStub = """
            {
    "http-request": {
        "path": "http://localhost:9000/",
        "method": "POST",
        "headers": {
            "Accept": "text/plain, application/json, application/*+json, */*",
            "Content-Type": "text/plain;charset=ISO-8859-1",
            "User-Agent": "Java/1.8.0_241",
            "Host": "localhost:9000",
            "Proxy-Connection": "keep-alive",
            "Content-Length": "2"
        },
        "body": "10"
    },
    "http-response": {
        "status": 200,
        "body": "100",
        "status-text": "OK",
        "headers": {
            "Vary": "Origin",
            "X-Qontract-Result": "success",
            "Content-Length": "3",
            "Content-Type": "text/plain",
            "Connection": "keep-alive"
        }
    }
}
        """.trim()

        val generatedContracts = File("./build/generatedContracts")
        if(generatedContracts.exists())
            generatedContracts.deleteRecursively()
        generatedContracts.mkdirs()

        val flags = mutableListOf<String>()
        val receivedContent = mutableListOf<String>()
        val receivedPaths = mutableListOf<String>()

        val fakeFileWriter = object : FileWriter {
            override fun createDirectory() {
                flags.add("createDirectory")
            }

            override fun writeText(path: String, content: String) {
                receivedPaths.add(path)
                receivedContent.add(content)
            }
        }

        HttpStub(simpleFeature).use {
            Proxy(host = "localhost", port = 9001, "", fakeFileWriter).use { proxy ->
                val restProxy = java.net.Proxy(java.net.Proxy.Type.HTTP, InetSocketAddress("localhost", 9001))
                val requestFactory = SimpleClientHttpRequestFactory()
                requestFactory.setProxy(restProxy)
                val client = RestTemplate(requestFactory)
                val response = client.postForEntity("http://localhost:9000/", "10", String::class.java)

                assertThat(response.statusCodeValue).isEqualTo(200)
                assertThatNoException().isThrownBy { response.body!!.toInt() }
            }
        }

        assertThat(receivedContent.toList().map { it.trim() }).isEqualTo(listOf(generatedContract, generatedStub))

        assertThat(flags.toList()).isEqualTo(listOf("createDirectory"))
        assertThat(receivedPaths.toList()).isEqualTo(listOf("new_feature.qontract", "stub0.json"))
    }

    @Test
    fun `basic test of the reverse proxy`() {
        val generatedContract = """
            Feature: New feature
  Scenario: POST /
    When POST /
    And request-header Accept (string)
    And request-header Content-Type (string)
    And request-header User-Agent (string)
    And request-header Host (string)
    And request-header Connection (string)
    And request-header Content-Length (number)
    And request-body (RequestBody: string)
    Then status 200
    And response-header Connection (string)
    And response-body (number)
  
    Examples:
    | Accept | Content-Type | User-Agent | Host | Connection | Content-Length | RequestBody |
    | text/plain, application/json, application/*+json, */* | text/plain;charset=ISO-8859-1 | Java/1.8.0_241 | localhost:9001 | keep-alive | 2 | 10 |
        """.trim()

        val generatedStub = """
            {
    "http-request": {
        "path": "/",
        "method": "POST",
        "headers": {
            "Accept": "text/plain, application/json, application/*+json, */*",
            "Content-Type": "text/plain;charset=ISO-8859-1",
            "User-Agent": "Java/1.8.0_241",
            "Host": "localhost:9001",
            "Connection": "keep-alive",
            "Content-Length": "2"
        },
        "body": "10"
    },
    "http-response": {
        "status": 200,
        "body": "100",
        "status-text": "OK",
        "headers": {
            "Vary": "Origin",
            "X-Qontract-Result": "success",
            "Content-Length": "3",
            "Content-Type": "text/plain",
            "Connection": "keep-alive"
        }
    }
}
        """.trim()

        val generatedContracts = File("./build/generatedContracts")
        if(generatedContracts.exists())
            generatedContracts.deleteRecursively()
        generatedContracts.mkdirs()

        val flags = mutableListOf<String>()
        val receivedContent = mutableListOf<String>()
        val receivedPaths = mutableListOf<String>()

        val fakeFileWriter = object : FileWriter {
            override fun createDirectory() {
                flags.add("createDirectory")
            }

            override fun writeText(path: String, content: String) {
                receivedPaths.add(path)
                receivedContent.add(content)
            }
        }

        HttpStub(simpleFeature).use {
            Proxy(host = "localhost", port = 9001, "http://localhost:9000", fakeFileWriter).use { proxy ->
                val client = RestTemplate()
                val response = client.postForEntity("http://localhost:9001/", "10", String::class.java)

                assertThat(response.statusCodeValue).isEqualTo(200)
                assertThatNoException().isThrownBy { response.body!!.toInt() }
            }
        }

        assertThat(receivedContent.toList().map { it.trim() }).isEqualTo(listOf(generatedContract, generatedStub))

        assertThat(flags.toList()).isEqualTo(listOf("createDirectory"))
        assertThat(receivedPaths.toList()).isEqualTo(listOf("new_feature.qontract", "stub0.json"))
    }
}
