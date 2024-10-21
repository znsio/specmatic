package application.exampleGeneration.openApiExamples

import application.captureStandardOutput
import io.specmatic.conversions.ExampleFromFile
import io.specmatic.core.QueryParameters
import io.specmatic.core.SPECMATIC_STUB_DICTIONARY
import io.specmatic.core.pattern.parsedJSONObject
import io.specmatic.core.utilities.Flags
import io.specmatic.core.value.JSONArrayValue
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.Value
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class OpenApiExamplesGenerateTest {
    companion object {
        fun generateExamples(specFile: File, examplesDir: File): Triple<String, Int, List<File>> {
            return OpenApiExamplesGenerate().also { it.contractFile = specFile }.let {
                val (output, exitCode) = captureStandardOutput { it.call() }
                Triple(output, exitCode, examplesDir.listFiles()?.toList() ?: emptyList())
            }
        }

        private val externalDictionaryWithoutHeaders = parsedJSONObject("""
                {
                    "QUERY-PARAMS.name": "Jane Doe",
                    "QUERY-PARAMS.address": "123-Main-Street",
                    "PATH-PARAMS.name": "Jane-Doe",
                    "PATH-PARAMS.address": "123-Main-Street",
                    "Tracker.name": "Jane Doe",
                    "Tracker.address": "123-Main-Street",
                    "Tracker.trackerId": 100,
                    "Tracker_FVO.name": "Jane Doe",
                    "Tracker_FVO.address": "123-Main-Street"
                }
                """.trimIndent())

        private val externalDictionary = parsedJSONObject("""
                {
                    "HEADERS.Authentication": "Bearer 123",
                    "QUERY-PARAMS.name": "Jane Doe",
                    "QUERY-PARAMS.address": "123-Main-Street",
                    "PATH-PARAMS.name": "Jane-Doe",
                    "PATH-PARAMS.address": "123-Main-Street",
                    "Tracker.name": "Jane Doe",
                    "Tracker.address": "123-Main-Street",
                    "Tracker.trackerId": 100,
                    "Tracker_FVO.name": "Jane Doe",
                    "Tracker_FVO.address": "123-Main-Street"
                }
                """.trimIndent())

        fun assertHeaders(headers: Map<String, String>, apiKey: String) {
            assertThat(headers["Authentication"]).isEqualTo(apiKey)
        }

        fun assertPathParameters(path: String?, name: String, address: String) {
            assertThat(path).contains("/generate/names/$name/address/$address")
        }

        fun assertQueryParameters(queryParameters: QueryParameters, name: String, address: String) {
            assertThat(queryParameters.getValues("name")).contains(name)
            assertThat(queryParameters.getValues("address")).contains(address)
        }

        fun assertBody(body: Value, name: String, address: String) {
            body as JSONObjectValue
            assertThat(body.findFirstChildByPath("name")?.toStringLiteral()).isEqualTo(name)
            assertThat(body.findFirstChildByPath("address")?.toStringLiteral()).isEqualTo(address)
        }
    }

    @AfterEach
    fun cleanUp() {
        val examplesFolder = File("src/test/resources/specifications/tracker_examples")
        if (examplesFolder.exists()) {
            examplesFolder.listFiles()?.forEach { it.delete() }
            examplesFolder.delete()
        }
    }

    @Test
    fun `should fail with error for invalid contract file`(@TempDir tempDir: File) {
        val specFile = tempDir.resolve("spec.graphqls")
        val examplesDir = tempDir.resolve("spec_examples")

        specFile.createNewFile()

        val (stdOut, exitCode, _) = generateExamples(specFile, examplesDir)
        println(stdOut)

        assertThat(exitCode).isEqualTo(1)
        assertThat(stdOut).contains("spec.graphqls - File extension must be one of yaml, yml, json")
    }

    @Test
    fun `should generate an example when it is missing`(@TempDir tempDir: File) {
        val specFile = tempDir.resolve("spec.yaml")
        val examplesDir = tempDir.resolve("spec_examples")

        specFile.createNewFile()
        val spec = """
openapi: 3.0.0
info:
  title: Product API
  version: 1.0.0
paths:
  /product/{id}:
    get:
      summary: Get product details
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: integer
      responses:
        '200':
          description: Product details
          content:
            application/json:
              schema:
                type: object
                properties:
                  id:
                    type: integer
                  name:
                    type: string
                  price:
                    type: number
                    format: float
        """.trimIndent()
        specFile.writeText(spec)

        examplesDir.deleteRecursively()
        examplesDir.mkdirs()

        val (stdOut, exitCode, examples) = generateExamples(specFile, examplesDir)
        println(stdOut)

        assertThat(exitCode).isEqualTo(0)
        assertThat(stdOut).contains("1 example(s) created, 0 example(s) already existed, 0 example(s) failed")

        assertThat(examples).hasSize(1)
        assertThat(examples.single().name).matches("product_[0-9]*_GET_200_1.json")
    }

    @Test
    fun `should retain existing examples and generate missing ones`(@TempDir tempDir: File) {
        val specFile = tempDir.resolve("spec.yaml")
        val examplesDir = tempDir.resolve("spec_examples")

        specFile.createNewFile()
        val spec = """
openapi: 3.0.0
info:
  title: Product API
  version: 1.0.0
paths:
  /product:
    get:
      summary: Get product details
      responses:
        '200':
          description: Product details
          content:
            application/json:
              schema:
                schema:
                  type: array
                  items:
                    type: object
                    properties:
                      id:
                        type: integer
                      name:
                        type: string
                      price:
                        type: number
                        format: float
  /product/{id}:
    get:
      summary: Get product details
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: integer
      responses:
        '200':
          description: Product details
          content:
            application/json:
              schema:
                type: object
                properties:
                  id:
                    type: integer
                  name:
                    type: string
                  price:
                    type: number
                    format: float
        """.trimIndent()
        specFile.writeText(spec)

        examplesDir.deleteRecursively()
        examplesDir.mkdirs()

        val example = """
{
  "http-request": {
    "method": "GET",
    "path": "/product/1"
  },
  "http-response": {
    "status": 200,
    "body": {
      "id": 1,
      "name": "Laptop",
      "price": 1000.99
    },
    "headers": {
      "Content-Type": "application/json"
    }
  }
}
        """.trimIndent()
        val exampleFile = examplesDir.resolve("example.json")
        exampleFile.writeText(example)

        val (stdOut, exitCode, examples) = generateExamples(specFile, examplesDir)
        println(stdOut)

        assertThat(exitCode).isEqualTo(0)
        assertThat(stdOut).contains("Using existing example(s) for GET /product/(id:number) -> 200")
            .contains(exampleFile.absolutePath)
            .contains("1 example(s) created, 1 example(s) already existed, 0 example(s) failed")

        assertThat(examples).hasSize(2)
        assertThat(examples.map { it.name }).containsExactlyInAnyOrder("example.json", "product_GET_200_1.json")
        assertThat(examples.find { it.name == "example.json" }?.readText() ?: "")
            .contains(""""name": "Laptop"""")
            .contains(""""price": 1000.99""")

        val generatedExample = examples.first { it.name == "product_GET_200_1.json" }
        assertThat(generatedExample.readText()).contains(""""path": "/product"""")
    }

    @Test
    fun `should generate only 2xx examples by default, when extensive is false`(@TempDir tempDir: File) {
        val specFile = tempDir.resolve("spec.yaml")
        val examplesDir = tempDir.resolve("spec_examples")

        specFile.createNewFile()
        val spec = """
openapi: 3.0.0
info:
  title: Product API
  version: 1.0.0
paths:
  /product/{id}:
    get:
      summary: Get product details
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: integer
      responses:
        '200':
          description: Product details
          content:
            application/json:
              schema:
                type: object
                properties:
                  id:
                    type: integer
                  name:
                    type: string
                  price:
                    type: number
                    format: float
        '404':
          description: Bad Request - Invalid input
          content:
            application/json:
              schema:
                type: object
                properties:
                  error:
                    type: string
                    example: "Invalid product ID"
        '500':
          description: Internal Server Error - Unexpected error occurred
          content:
            application/json:
              schema:
                type: object
                properties:
                  error:
                    type: string
                    example: "An unexpected error occurred"
        """.trimIndent()
        specFile.writeText(spec)

        examplesDir.deleteRecursively()
        examplesDir.mkdirs()

        val (stdOut, exitCode, examples) = generateExamples(specFile, examplesDir)
        println(stdOut)

        assertThat(exitCode).isEqualTo(0)
        assertThat(stdOut).doesNotContain("Using dictionary file")
        assertThat(stdOut).contains("${examples.size} example(s) created, 0 example(s) already existed, 0 example(s) failed")

        assertThat(examples).allSatisfy {
            val example = ExampleFromFile(it)
            val response = example.response
            assertThat(response.status).isGreaterThanOrEqualTo(200).isLessThan(300)
        }
    }

    @Test
    fun `should generate with random values when no dictionary is provided`() {
        val contractFile = File("src/test/resources/specifications/tracker.yaml")
        val examplesDir = contractFile.parentFile.canonicalFile.resolve("tracker_examples")

        val (stdOut, exitCode, examples) = generateExamples(contractFile, examplesDir)
        println(stdOut)

        assertThat(exitCode).isEqualTo(0)
        assertThat(stdOut).doesNotContain("Using dictionary file")
        assertThat(stdOut).contains("${examples.size} example(s) created, 0 example(s) already existed, 0 example(s) failed")

        examples.forEach {
            val example = ExampleFromFile(it)
            val request = example.request
            val response = example.response
            val responseBody = response.body as JSONArrayValue

            assertThat(request.headers["Authentication"])
                .withFailMessage("Header values should be randomly generated")
                .isNotEqualTo("Bearer 123")

            when(request.method) {
                "POST" -> {
                    val body = request.body as JSONObjectValue
                    assertThat(body.findFirstChildByPath("name")?.toStringLiteral()).isNotEqualTo("John-Doe")
                    assertThat(body.findFirstChildByPath("address")?.toStringLiteral()).isNotEqualTo("123-Main-Street")

                }
                "GET" -> {
                    val queryParameters = request.queryParams
                    assertThat(queryParameters.getValues("name")).doesNotContain("John-Doe")
                    assertThat(queryParameters.getValues("address")).doesNotContain("123-Main-Street")
                }
                "DELETE" -> {
                    val path = request.path as String
                    assertThat(path).doesNotContain("/generate/names/John-Doe/address/123-Main-Street")
                    assertThat(path.trim('/').split('/').last()).isNotEqualTo("(string)")
                }
                else -> throw IllegalArgumentException("Unexpected method ${request.method}")
            }

            assertThat(responseBody.list).allSatisfy {value ->
                value as JSONObjectValue
                assertThat(value.findFirstChildByPath("name")?.toStringLiteral()).isNotEqualTo("John Doe")
                assertThat(value.findFirstChildByPath("address")?.toStringLiteral()).isNotEqualTo("123 Main Street")
            }
        }
    }

    @Test
    fun `should use values from dictionary when provided`(@TempDir tempDir: File) {
        val dictionaryFileName = "dictionary.json"
        val contractFile = File("src/test/resources/specifications/tracker.yaml")
        val examplesDir = contractFile.parentFile.canonicalFile.resolve("tracker_examples")

        val dictionaryFile = tempDir.resolve(dictionaryFileName)
        dictionaryFile.writeText(externalDictionary.toStringLiteral())

        val (stdOut, exitCode, examples) = Flags.using(SPECMATIC_STUB_DICTIONARY to dictionaryFile.absolutePath) {
            generateExamples(contractFile, examplesDir)
        }
        println(stdOut)

        assertThat(exitCode).isEqualTo(0)
        assertThat(stdOut).contains("Using dictionary file ${dictionaryFile.absolutePath}")
        assertThat(stdOut).contains("${examples.size} example(s) created, 0 example(s) already existed, 0 example(s) failed")

        examples.forEach {
            val example = ExampleFromFile(it)
            val request = example.request
            val response = example.response

            assertHeaders(request.headers, "Bearer 123")

            when(request.method) {
                "POST" -> assertBody(request.body, "Jane Doe", "123-Main-Street")
                "GET"  -> assertQueryParameters(request.queryParams, "Jane Doe", "123-Main-Street")
                "DELETE" -> {
                    assertPathParameters(request.path, "Jane-Doe", "123-Main-Street")
                    assertThat(request.path!!.trim('/').split('/').last()).isNotEqualTo("(string)")
                }
                else -> throw IllegalArgumentException("Unexpected method ${request.method}")
            }

            val jsonResponseBody = response.body as JSONArrayValue
            assertThat(jsonResponseBody.list).allSatisfy { value ->
                value as JSONObjectValue
                assertBody(value, "Jane Doe", "123-Main-Street")
            }
        }
    }

    @Test
    fun `should only replace values if key is in dictionary`(@TempDir tempDir: File) {
        val dictionaryFileName = "dictionary.json"
        val contractFile = File("src/test/resources/specifications/tracker.yaml")
        val examplesDir = contractFile.parentFile.canonicalFile.resolve("tracker_examples")

        val dictionaryFile = tempDir.resolve(dictionaryFileName)
        dictionaryFile.writeText(externalDictionaryWithoutHeaders.toStringLiteral())

        val (stdOut, exitCode, examples) = Flags.using(SPECMATIC_STUB_DICTIONARY to dictionaryFile.absolutePath) {
            generateExamples(contractFile, examplesDir)
        }
        println(stdOut)

        assertThat(exitCode).isEqualTo(0)
        assertThat(stdOut).contains("Using dictionary file ${dictionaryFile.absolutePath}")
        assertThat(stdOut).contains("${examples.size} example(s) created, 0 example(s) already existed, 0 example(s) failed")

        examples.forEach {
            val example = ExampleFromFile(it)
            val request = example.request
            val response = example.response
            val responseBody = response.body as JSONArrayValue

            assertThat(request.headers["Authentication"])
                .withFailMessage("Header values should be randomly generated")
                .isNotEqualTo("Bearer 123")

            when(request.method) {
                "POST" -> assertBody(request.body, "Jane Doe", "123-Main-Street")
                "GET"  -> assertQueryParameters(request.queryParams, "Jane Doe", "123-Main-Street")
                "DELETE" -> {
                    assertPathParameters(request.path, "Jane-Doe", "123-Main-Street")
                    assertThat(request.path!!.trim('/').split('/').last()).isNotEqualTo("(string)")
                }
                else -> throw IllegalArgumentException("Unexpected method ${request.method}")
            }

            assertThat(responseBody.list).allSatisfy { value ->
                value as JSONObjectValue
                assertBody(value, "Jane Doe", "123-Main-Street")
            }
        }
    }
}