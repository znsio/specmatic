package io.specmatic.conversions

import io.specmatic.core.*
import io.specmatic.core.pattern.parsedJSONObject
import io.specmatic.core.value.StringValue
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

        val generatedHeaders = HttpHeadersPattern().newBasedOn(row, Resolver()).first().value.generate(Resolver())
        assertThat(generatedHeaders).containsEntry("X-Tra", "info")
    }

    @Test
    fun `should be able to read and load multi-part form data from example file`() {
        val exampleContent = """{
        "http-request": {
            "method": "POST",
            "path": "/products",
            "headers": {
                "Content-Type": "multipart/form-data"
            },
            "$MULTIPART_FORMDATA_JSON_KEY": [
                {
                    "name": "data",
                    "content": "abc123",
                    "contentType": "text/plain"
                },
                {
                    "name": "byteData",
                    "content": "@[-24, -109, -101, -32, -114, -101, -100, -32, -115, -99, -97, -110, -110, -101, -114]",
                    "contentType": ""
                },
                {
                    "name": "image",
                    "filename": "@image.png",
                    "contentType": "image/png",
                    "contentEncoding": "base64"
                }
            ]
        },
        "http-response": {
            "status": 200,
            "status-text": "OK"
        }
        }""".trimIndent()
        val example = ExampleFromFile(parsedJSONObject(exampleContent), File("./data.json"))

        assertThat(example.request.multiPartFormData).containsExactlyInAnyOrder(
            MultiPartContentValue("data", StringValue("abc123"), specifiedContentType = "text/plain"),
            MultiPartContentValue(
                name = "byteData",
                content = StringValue("@[-24, -109, -101, -32, -114, -101, -100, -32, -115, -99, -97, -110, -110, -101, -114]"),
                specifiedContentType = ""
            ),
            MultiPartFileValue(name = "image", filename = "image.png", contentType = "image/png", contentEncoding = "base64")
        )
    }

    @Test
    fun `should be able to read and load form-fields from example file`() {
        val exampleContent = """{
        "http-request": {
            "method": "POST",
            "path": "/products",
            "headers": {
                "Content-Type": "application/x-www-form-urlencoded"
            },
            "$FORM_FIELDS_JSON_KEY": {
                "name": "John Doe",
                "age": 10
            }
        },
        "http-response": {
            "status": 200,
            "status-text": "OK"
        }
        }""".trimIndent()
        val example = ExampleFromFile(parsedJSONObject(exampleContent), File("./data.json"))

        assertThat(example.request.formFields).isEqualTo(mapOf("name" to "John Doe", "age" to "10"))
    }
}