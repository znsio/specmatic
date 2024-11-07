package io.specmatic.core.examples.server

import io.specmatic.conversions.ExampleFromFile
import io.specmatic.core.HttpRequest
import io.specmatic.core.HttpResponse
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.NumberValue
import io.specmatic.core.value.StringValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

class ExamplesInteractiveServerTest {
    @Nested
    inner class ExternaliseInlineExamplesTests {

        @Test
        fun `should externalise the inline examples into an examples folder`() {
            var examplesDir: File? = null
            try {
                val contractFile = File("src/test/resources/openapi/has_multiple_inline_examples.yaml")
                examplesDir = ExamplesInteractiveServer.externaliseInlineExamples(contractFile)

                val externalisedExampleFiles = examplesDir.listFiles()?.map { it.name }
                assertThat(externalisedExampleFiles).containsExactlyInAnyOrder(
                    "products_POST_201_1.json",
                    "findAvailableProducts_GET_200_2.json",
                    "findAvailableProducts_GET_503_3.json",
                    "orders_GET_200_4.json"
                )
                val postProductsExample = examplesDir.listFiles()?.first {
                    val exampleFromFile = ExampleFromFile(it)
                    exampleFromFile.request.path == "/products" && exampleFromFile.requestMethod == "POST"
                }

                val postProductsExampleFromFile = ExampleFromFile(postProductsExample!!)
                assertThat(postProductsExampleFromFile.request).isEqualTo(
                    HttpRequest(
                        method = "POST",
                        path = "/products",
                        headers = mapOf("Content-Type" to "application/json"),
                        body = JSONObjectValue(
                            mapOf(
                                "name" to StringValue("iPhone"),
                                "type" to StringValue("gadget"),
                                "inventory" to NumberValue(100)
                            )
                        )
                    )
                )
                assertThat(postProductsExampleFromFile.response).isEqualTo(
                    HttpResponse(
                        status = 201,
                        body = JSONObjectValue(
                            mapOf(
                                "id" to NumberValue(1)
                            )
                        ),
                        headers = emptyMap()
                    )
                )
            } finally {
                examplesDir?.deleteRecursively()
            }
        }
    }
}