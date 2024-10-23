package io.specmatic.core.examples.server

import org.junit.jupiter.api.Test
import java.io.File

class ExamplesInteractiveServerTest {
    @Test
    fun should() {
        ExamplesInteractiveServer.createDictionaryFrom(
            File("src/test/resources/openapi/has_nested_response_body_schema.yaml"),
            File("src/test/resources/openapi/has_nested_response_body_schema_examples/example.json")
        )
    }
}