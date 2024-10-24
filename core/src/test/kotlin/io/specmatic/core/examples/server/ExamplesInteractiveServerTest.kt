package io.specmatic.core.examples.server

import io.specmatic.core.pattern.parsedJSONObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class ExamplesInteractiveServerTest {
    @Test
    fun `should extract the data from the externalised examples to a dictionary file`() {
        var dictionaryFile: File? = null
        try {
            dictionaryFile = ExamplesInteractiveServer.createDictionaryFrom(
                File("src/test/resources/openapi/has_nested_response_body_schema.yaml"),
                File("src/test/resources/openapi/has_nested_response_body_schema_examples/example.json")
            )
            val dictionary = parsedJSONObject(dictionaryFile.readText()).jsonObject

            assertTrue(dictionaryFile.exists())
            assertThat(dictionary.size).isEqualTo(13)
            assertThat(dictionary.containsKey("Employee.id")).isTrue()
            assertThat(dictionary.containsKey("Employee.employer.name")).isTrue()
            assertThat(dictionary.containsKey("Location.street")).isTrue()
            assertThat(dictionary.containsKey("ContactDetails.email")).isTrue()
            println()
        } finally {
            dictionaryFile?.deleteRecursively()
        }
    }
}