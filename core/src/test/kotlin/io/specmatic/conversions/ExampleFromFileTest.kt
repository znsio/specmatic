package io.specmatic.conversions

import io.specmatic.core.NoBodyValue
import io.specmatic.core.SpecmaticConfig
import io.specmatic.core.pattern.parsedJSONObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

class ExampleFromFileTest {
    @Test
    fun `it should query params from the url if present`() {
        val example = """
            {
                "http-request": {
                    "method": "GET",
                    "path": "/hello?world=true"
                },
                "http-response": {
                    "status": 200,
                    "body": "ok"
                }
            }
        """.trimIndent().let {

            ExampleFromFile(parsedJSONObject(it), File("./data.json"))
        }

        assertThat(example.queryParams).containsExactlyEntriesOf(mapOf("world" to "true"))
    }

    @Test
    fun `it should combine query params from the url if present with those from the block`() {
        val example = """
            {
                "http-request": {
                    "method": "GET",
                    "path": "/query?hello=true",
                    "query": {
                        "world": "true"
                    }
                },
                "http-response": {
                    "status": 200,
                    "body": "ok"
                }
            }
        """.trimIndent().let {

            ExampleFromFile(parsedJSONObject(it), File("./data.json"))
        }

        assertThat(example.queryParams).containsExactlyEntriesOf(mapOf("hello" to "true", "world" to "true"))
    }

    @Test
    fun `it should return no body and empty headers if response body and headers are null`() {
        val example = """
        {
            "http-request": {
                "method": "GET",
                "path": "/hello?world=true"
            },
            "http-response": {
                "status": 200
            }
        }
    """.trimIndent().let {
            ExampleFromFile(parsedJSONObject(it), File("./data.json"))
        }

        val response = example.response

        assertThat(response.body).isEqualTo(NoBodyValue)
        assertThat(response.headers).isEmpty()
        assertThat(response.status).isEqualTo(200)
    }

    @Test
    fun `it should return a valid HttpRequest with the correct properties`() {
        val example = """
        {
            "http-request": {
                "method": "POST",
                "path": "/api/resource?filter=active",
                "headers": {
                    "Content-Type": "application/json"
                },
                "body": {
                    "key": "value"
                }
            },
            "http-response": {
                "status": 200,
                "body": "ok"
            }
        }
    """.trimIndent().let {
            ExampleFromFile(parsedJSONObject(it), File("./data.json"))
        }

        val request = example.request

        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/api/resource")
        assertThat(request.headers).containsEntry("Content-Type", "application/json")
        assertThat(request.body).isEqualTo(parsedJSONObject("""{"key":"value"}"""))
        assertThat(request.queryParams.asMap()).containsExactlyEntriesOf(mapOf("filter" to "active"))
    }

    @Test
    fun `should load extra header params from additional params file`() {
        val example = """
        {
            "http-request": {
                "method": "POST",
                "path": "/api/resource?filter=active",
                "headers": {
                    "Content-Type": "application/json"
                },
                "body": {
                    "key": "value"
                }
            },
            "http-response": {
                "status": 200,
                "body": "ok"
            }
        }
    """.trimIndent().let {
            ExampleFromFile(parsedJSONObject(it), File("./data.json"))
        }

        val row =
            example.toRow(SpecmaticConfig(additionalExampleParamsFilePath = "src/test/resources/additionalParamsFile.json"))

        assertThat(row.requestExample?.headers.orEmpty()).containsEntry("X-Tra", "info")
    }
}