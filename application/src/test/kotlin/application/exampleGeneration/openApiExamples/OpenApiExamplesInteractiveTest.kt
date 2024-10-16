package application.exampleGeneration.openApiExamples

import io.ktor.http.*
import io.specmatic.conversions.ExampleFromFile
import io.specmatic.core.HttpRequest
import io.specmatic.core.HttpResponse
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.StringValue
import io.specmatic.test.HttpClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.URI
import java.net.URL
import kotlin.concurrent.thread

@Suppress("SameParameterValue")
class OpenApiExamplesInteractiveTest {
    companion object {
        private lateinit var serverThread: Thread
        private val trackerContract = File("src/test/resources/specifications/tracker.yaml")
        private val testServerHostPort = URL("http://localhost:8080").toURI()
        private val exampleServerClient = HttpClient("http://localhost:9001/_specmatic/examples")
        private val execCommand = OpenApiExamplesInteractive()

        private fun waitForServer(maxRetries: Int = 10, sleepDuration: Long = 200) {
            repeat(maxRetries) {
                try {
                    val resp = HttpClient("http://localhost:9001", timeoutInMilliseconds = 2000)
                        .execute(HttpRequest().updatePath("actuator/health").updateMethod("GET")).status

                    if (resp == 200) {
                        return println("Server is up and healthy.")
                    }
                } catch (_: Exception) {
                    Thread.sleep(sleepDuration)
                }
            }

            throw IllegalStateException("Server did not start after $maxRetries attempts.")
        }

        private fun createHttpRequest(path: String, method: String, body: String? = null): HttpRequest {
            return HttpRequest().updatePath(path).updateMethod(method).updateBody(body)
        }

        private fun exampleGenerateRequest(contractFile: File?, jsonRequest: String): HttpResponse {
            val generateRequest = createHttpRequest("/generate", "POST", jsonRequest)
            return withContractFile(contractFile) {
                exampleServerClient.execute(generateRequest)
            }
        }

        private fun exampleValidateRequest(contractFile: File?, jsonRequest: String): HttpResponse {
            val validateRequest = createHttpRequest("/validate", "POST", jsonRequest)
            return withContractFile(contractFile) {
                exampleServerClient.execute(validateRequest)
            }
        }

        private fun exampleTestRequest(contractFile: File?, sutBaseUrl: URI, jsonRequest: String): HttpResponse {
            val testRequest = createHttpRequest("/test", "POST", jsonRequest)
            return withContractFile(contractFile) {
                withSutBaseUrl(sutBaseUrl.toString()) {
                    exampleServerClient.execute(testRequest)
                }
            }
        }

        private fun exampleContentRequest(contractFile: File?, jsonRequest: String): HttpResponse {
            val exampleContentRequest = createHttpRequest("/content", "POST", jsonRequest)
            return withContractFile(contractFile) {
                exampleServerClient.execute(exampleContentRequest)
            }
        }

        private fun exampleHtmlPageRequest(contractFile: File?, jsonRequest: String): HttpResponse {
            val exampleContentRequest = createHttpRequest("/", "POST", jsonRequest)
            return withContractFile(contractFile) {
                exampleServerClient.execute(exampleContentRequest)
            }
        }

        private fun exampleHtmlPageRequest(contractFile: File?): HttpResponse {
            val request = HttpRequest().updatePath("/").updateMethod("GET")
            return withContractFile(contractFile) {
                exampleServerClient.execute(request)
            }
        }

        private fun createJsonGenerateRequest(path: String, method: String, response: String): String {
            return """
            {
                "path": { "rawValue": "$path", "value": "$path" },
                "method": { "rawValue": "$method", "value": "$method" },
                "response": { "rawValue": "$response", "value": "$response" }
            }
        """.trimIndent()
        }

        private fun withContractFile(contractFile: File?, block: () -> HttpResponse): HttpResponse {
            val originalContractFile = execCommand.contractFile
            execCommand.contractFile = contractFile
            try {
                return block()
            } finally {
                execCommand.contractFile = originalContractFile
            }
        }

        private fun withSutBaseUrl(sutBaseUrl: String, block: () -> HttpResponse): HttpResponse {
            val originalSutBaseUrl = execCommand.sutBaseUrl
            execCommand.sutBaseUrl = sutBaseUrl
            try {
                return block()
            } finally {
                execCommand.sutBaseUrl = originalSutBaseUrl
            }
        }

        @JvmStatic
        @BeforeAll
        fun startServer() {
            serverThread = thread(start = true) { execCommand.call() }
            waitForServer()
        }

        @JvmStatic
        @AfterAll
        fun stopServer() {
            serverThread.interrupt()
            serverThread.join()
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
    fun `should generate example from request`() {
        val jsonRequest = createJsonGenerateRequest("/generate", "POST", "200")

        val response = exampleGenerateRequest(trackerContract, jsonRequest)
        assertThat(response.status).isEqualTo(HttpStatusCode.OK.value)
        assertThat(response.body).isInstanceOf(JSONObjectValue::class.java)

        val responseBody = response.body as JSONObjectValue
        assertThat(responseBody.findFirstChildByPath("exampleFilePath")?.toStringLiteral()).contains("generate_POST_200.json")
        assertThat(responseBody.findFirstChildByPath("status")?.toStringLiteral()).isEqualTo("CREATED")
    }

    @Test
    fun `should validate example from request`() {
        val generateJsonRequest = createJsonGenerateRequest("/generate", "POST", "200")

        val generateResponse = exampleGenerateRequest(trackerContract, generateJsonRequest)
        assertThat(generateResponse.status).isEqualTo(HttpStatusCode.OK.value)
        assertThat(generateResponse.body).isInstanceOf(JSONObjectValue::class.java)

        val generateResponseBody = generateResponse.body as JSONObjectValue
        val exampleFilePath = generateResponseBody.findFirstChildByPath("exampleFilePath")!!
        val validateJsonRequest = """
            {
                "exampleFile": ${exampleFilePath.displayableValue()}
            }
        """.trimIndent()

        val validateResponse = exampleValidateRequest(trackerContract, validateJsonRequest)
        assertThat(validateResponse.status).isEqualTo(HttpStatusCode.OK.value)
        assertThat(validateResponse.body).isInstanceOf(JSONObjectValue::class.java)

        val validateResponseBody = validateResponse.body as JSONObjectValue
        assertThat(validateResponseBody.findFirstChildByPath("exampleFilePath")).isEqualTo(exampleFilePath)
        assertThat(validateResponseBody.findFirstChildByPath("error")?.toStringLiteral()).isBlank()
    }

    @Test
    fun `should retrieve content of generated example file from request`() {
        val generateJsonRequest = createJsonGenerateRequest("/generate", "POST", "200")
        val generateResponse = exampleGenerateRequest(trackerContract, generateJsonRequest)
        assertThat(generateResponse.status).isEqualTo(HttpStatusCode.OK.value)
        assertThat(generateResponse.body).isInstanceOf(JSONObjectValue::class.java)

        val generateResponseBody = generateResponse.body as JSONObjectValue
        val exampleFilePath = generateResponseBody.findFirstChildByPath("exampleFilePath")!!
        val getExampleContentJsonRequest = """
            {
                "exampleFile": ${exampleFilePath.displayableValue()}
            }
        """.trimIndent()

        val getExampleContentResponse = exampleContentRequest(trackerContract, getExampleContentJsonRequest)
        assertThat(getExampleContentResponse.status).isEqualTo(HttpStatusCode.OK.value)
        assertThat(getExampleContentResponse.body).isInstanceOf(JSONObjectValue::class.java)

        val getExampleContentResponseBody = getExampleContentResponse.body as JSONObjectValue
        val content = getExampleContentResponseBody.findFirstChildByPath("content")!!

        assertThat(content.toStringLiteral()).isEqualTo(File(exampleFilePath.toStringLiteral()).readText())
    }

    @Test
    fun `invalid or non existing example file request should return error`() {
        val validateJsonRequest = """
            {
                "exampleFile": "invalid_file_path.json"
            }
        """.trimIndent()

        val validateResponse = exampleValidateRequest(trackerContract, validateJsonRequest)
        assertThat(validateResponse.status).isEqualTo(HttpStatusCode.BadRequest.value)
        assertThat(validateResponse.body).isInstanceOf(JSONObjectValue::class.java)

        val validateResponseBody = validateResponse.body as JSONObjectValue
        assertThat(validateResponseBody.findFirstChildByPath("error")!!.toStringLiteral())
            .contains("Example file does not exist").contains("invalid_file_path.json")

        val exampleContentResponse = exampleContentRequest(trackerContract, validateJsonRequest)
        assertThat(exampleContentResponse.status).isEqualTo(HttpStatusCode.BadRequest.value)
        assertThat(exampleContentResponse.body).isInstanceOf(JSONObjectValue::class.java)

        val exampleContentResponseBody = exampleContentResponse.body as JSONObjectValue
        assertThat(exampleContentResponseBody.findFirstChildByPath("error")!!.toStringLiteral())
            .contains("Example file does not exist").contains("invalid_file_path.json")
    }

    @Test
    fun `should be able to test example from request`() {
        val generateJsonRequest = createJsonGenerateRequest("/generate", "POST", "200")
        val generateResponse = exampleGenerateRequest(trackerContract, generateJsonRequest)
        assertThat(generateResponse.status).isEqualTo(HttpStatusCode.OK.value)
        assertThat(generateResponse.body).isInstanceOf(JSONObjectValue::class.java)

        val generateResponseBody = generateResponse.body as JSONObjectValue
        val exampleFilePath = generateResponseBody.findFirstChildByPath("exampleFilePath")!!
        val example = ExampleFromFile(File(exampleFilePath.toStringLiteral()))

        val testJsonRequest = """
            {
                "exampleFile": ${exampleFilePath.displayableValue()}
            }
        """.trimIndent()
        val testResponse = exampleTestRequest(trackerContract, testServerHostPort, testJsonRequest)
        assertThat(testResponse.status).isEqualTo(HttpStatusCode.OK.value)

        val testResponseBody = testResponse.body as JSONObjectValue
        assertThat(testResponseBody.findFirstChildByPath("result")?.toStringLiteral()).isEqualTo("Failed")
        assertThat(testResponseBody.findFirstChildByPath("details")?.toStringLiteral()).contains("Example test for generate_POST_200 has FAILED")
        assertThat(testResponseBody.findFirstChildByPath("testLog")?.toStringLiteral())
            .contains("POST /generate").contains(example.request.body.toStringLiteral())
    }

    @Test
    fun `initial page request should use existing examples correctly`() {
        val generateJsonRequest = createJsonGenerateRequest("/generate", "POST", "200")
        val generateResponse = exampleGenerateRequest(trackerContract, generateJsonRequest)
        assertThat(generateResponse.status).isEqualTo(HttpStatusCode.OK.value)
        assertThat(generateResponse.body).isInstanceOf(JSONObjectValue::class.java)

        val generateResponseBody = generateResponse.body as JSONObjectValue
        val exampleFilePath = generateResponseBody.findFirstChildByPath("exampleFilePath")!!
        assertThat(exampleFilePath.toStringLiteral()).isNotBlank()

        val htmlPageResponse = exampleHtmlPageRequest(trackerContract)
        val htmlText = (htmlPageResponse.body as StringValue).toStringLiteral()

        assertThat(htmlText).contains(trackerContract.name).contains(trackerContract.absolutePath)
        assertThat(htmlText).contains(exampleFilePath.toStringLiteral())
    }

    @Test
    fun `should use contract file sent in post request when no initial contract is set`(@TempDir tempDir: File) {
        val specFile = tempDir.resolve("spec.yaml")

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

        val generateJsonRequest = """
            {
                "contractFile": ${specFile.absolutePath.quote()}
            }
        """.trimIndent()

        val htmlPageResponse = exampleHtmlPageRequest(null, generateJsonRequest)
        assertThat(htmlPageResponse.status).isEqualTo(HttpStatusCode.OK.value)

        val htmlText = (htmlPageResponse.body as StringValue).toStringLiteral()
        assertThat(htmlText).contains(specFile.name).contains(specFile.absolutePath)
    }

    @Test
    fun `contract file specified in arguments should override post request`(@TempDir tempDir: File) {
        val specFile = tempDir.resolve("spec.yaml")

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

        val generateJsonRequest = """
            {
                "contractFile": ${specFile.absolutePath.quote()}
            }
        """.trimIndent()

        val htmlPageResponse = exampleHtmlPageRequest(trackerContract, generateJsonRequest)
        assertThat(htmlPageResponse.status).isEqualTo(HttpStatusCode.OK.value)

        val htmlText = (htmlPageResponse.body as StringValue).toStringLiteral()
        assertThat(htmlText).doesNotContain(specFile.name).doesNotContain(specFile.absolutePath)
        assertThat(htmlText).contains(trackerContract.name).contains(trackerContract.absolutePath)
    }

    @Test
    fun `invalid or non existing contract file request should return error`(@TempDir tempDir: File) {
        val nonExistingSpec = """
            {
                "contractFile": "invalid_file_path.yaml"
            }
        """.trimIndent()

        val nonExistingSpecResponse = exampleHtmlPageRequest(null, nonExistingSpec)
        assertThat(nonExistingSpecResponse.status).isEqualTo(HttpStatusCode.BadRequest.value)
        assertThat(nonExistingSpecResponse.body).isInstanceOf(JSONObjectValue::class.java)

        val nonExistingSpecBody = nonExistingSpecResponse.body as JSONObjectValue
        assertThat(nonExistingSpecBody.findFirstChildByPath("error")!!.toStringLiteral())
            .isEqualTo("Contract file not provided or does not exist, Please provide one via HTTP request or command line")

        val invalidSpec = tempDir.resolve("spec.graphql")
        invalidSpec.createNewFile()
        invalidSpec.writeText(nonExistingSpec)

        val invalidSpecRequest = """
            {
                "contractFile": ${invalidSpec.absolutePath.quote()}
            }
        """.trimIndent()

        val invalidSpecResponse = exampleHtmlPageRequest(null, invalidSpecRequest)
        assertThat(invalidSpecResponse.status).isEqualTo(HttpStatusCode.BadRequest.value)
        assertThat(invalidSpecResponse.body).isInstanceOf(JSONObjectValue::class.java)

        val invalidSpecBody = invalidSpecResponse.body as JSONObjectValue
        assertThat(invalidSpecBody.findFirstChildByPath("error")!!.toStringLiteral())
            .isEqualTo("Invalid Contract file ${invalidSpec.absolutePath} - File extension must be one of yaml, yml, json")
    }
}