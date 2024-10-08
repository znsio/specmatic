package application.exampleGeneration.openApiExamples

import application.captureStandardOutput
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class OpenApiExamplesValidateTest {
    companion object {
        fun validateExamples(specFile: File): Pair<String, Int> {
            return OpenApiExamplesValidate().also { it.contractFile = specFile }.let {
                val (output, exitCode) = captureStandardOutput { it.execute(specFile) }
                Pair(output, exitCode)
            }
        }

        fun validateSingleExample(specFile: File, exampleFile: File): Pair<String, Int> {
            return OpenApiExamplesValidate().also { it.contractFile = specFile; it.exampleFile = exampleFile }.let {
                val (output, exitCode) = captureStandardOutput { it.execute(specFile) }
                Pair(output, exitCode)
            }
        }
    }

    @Test
    fun `should display an error message for an invalid example`(@TempDir tempDir: File) {
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

        val example = """
{
  "http-request": {
    "method": "GET",
    "path": "/product/abc123"
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

        val (stdOut, exitCode) = validateExamples(specFile)
        println(stdOut)

        assertThat(exitCode).isNotEqualTo(0)
        assertThat(stdOut).contains("example.json has the following validation error(s)")
            .contains("""expected number but example contained""")
        assertThat(stdOut).contains("0 example(s) are valid. 1 example(s) are invalid")
    }

    @Test
    fun `should not display an error message when all examples are valid`(@TempDir tempDir: File) {
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

        val (stdOut, exitCode) = validateExamples(specFile)
        println(stdOut)

        assertThat(exitCode).isEqualTo(0)
        assertThat(stdOut).contains("example.json is valid")
        assertThat(stdOut).contains("1 example(s) are valid. 0 example(s) are invalid")
    }

    @Test
    fun `should not print an empty error when it sees an inline example for a filtered-out scenario`(@TempDir tempDir: File) {
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

        val example = """
{
  "http-request": {
    "method": "GET",
    "path": "/products/1"
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

        val (stdOut, exitCode) = validateExamples(specFile)
        println(stdOut)

        assertThat(exitCode).isNotEqualTo(0)
        assertThat(stdOut).contains("example.json has the following validation error(s)")
            .contains("No matching REST stub or contract found")
        assertThat(stdOut).contains("0 example(s) are valid. 1 example(s) are invalid")
    }

    @Test
    fun `should only validate the specified example and ignore others`(@TempDir tempDir: File) {
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
        '202':
          description: Request accepted for processing
          content:
            application/json:
              schema:
                type: object
                properties:
                  message:
                    type: string
                    example: "Request accepted for processing"
        """.trimIndent()
        specFile.writeText(spec)

        examplesDir.deleteRecursively()
        examplesDir.mkdirs()

        val (_, generationExitCode, examples) = OpenApiExamplesGenerateTest.generateExamples(specFile, examplesDir)

        assertThat(generationExitCode).isEqualTo(0)
        assertThat(examples.size).isEqualTo(2)

        assertThat(examples).allSatisfy{
            val (stdOut, exitCode) = validateSingleExample(specFile, it)
            println(stdOut)

            assertThat(exitCode).isEqualTo(0)
            assertThat(stdOut).contains("${it.name} is valid")

            val otherExamples = examples.filter { exFile -> exFile != it }
            otherExamples.forEach { otherExample ->
                assertThat(stdOut).doesNotContain(otherExample.name)
            }
        }
    }

    @Test
    fun `should fail validation with error on invalid file extension for specified example`(@TempDir tempDir: File) {
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
        val exampleFile = examplesDir.resolve("example.txt")
        exampleFile.writeText(example)

        val (stdOut, exitCode) = validateSingleExample(specFile, exampleFile)
        println(stdOut)

        assertThat(exitCode).isNotEqualTo(0)
        assertThat(stdOut).contains("Invalid Example file ${exampleFile.absolutePath}")
            .contains("File extension must be one of json")
    }
}
