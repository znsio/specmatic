package io.specmatic.conversions

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
}