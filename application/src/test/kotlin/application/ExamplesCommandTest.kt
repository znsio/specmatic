package application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ExamplesCommandTest {
    @Test
    fun `examples validate command should not print an empty error when it sees an inline example for a filtered-out scenario`(@TempDir tempDir: File) {
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

        val command = ExamplesCommand.Validate().also {
            it.contractFile = specFile
        }

        val (output, returnValue: Int) = captureStandardOutput {
            command.call()
        }

        println(output)

        assertThat(returnValue).isNotEqualTo(0)
        assertThat(output).contains("No matching REST stub or contract found")
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

        val command = ExamplesCommand.Validate().also {
            it.contractFile = specFile
        }

        val (output, returnValue: Int) = captureStandardOutput {
            command.call()
        }

        println(output)

        assertThat(returnValue).isNotEqualTo(0)
        assertThat(output).contains("""expected number but example contained""")
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

        val command = ExamplesCommand.Validate().also {
            it.contractFile = specFile
        }

        val (output, returnValue: Int) = captureStandardOutput {
            command.call()
        }

        println(output)

        assertThat(returnValue).isEqualTo(0)
        assertThat(output).contains("are valid")
    }

    @Test
    fun `should generate an example if missing`(@TempDir tempDir: File) {
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

        ExamplesCommand().also {
            it.contractFile = specFile
        }.call()

        val examplesCreated = examplesDir.walk().filter { it.isFile }.toList()

        assertThat(examplesCreated).hasSize(1)
        assertThat(examplesCreated.single().name).matches("product_[0-9]*_GET_200.json")

    }

    @Test
    fun `should generate only the missing examples and leave the existing examples as is`(@TempDir tempDir: File) {
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

        ExamplesCommand().also {
            it.contractFile = specFile
        }.call()

        val examplesCreated = examplesDir.walk().filter { it.isFile }.toList()

        assertThat(examplesCreated).hasSize(2)
        println(examplesCreated.map { it.name })
        assertThat(examplesCreated.filter { it.name == "example.json" }).hasSize(1)
        assertThat(examplesCreated.filter { it.name == "product_GET_200.json" }).hasSize(1)

        assertThat(examplesCreated.find { it.name == "example.json" }?.readText() ?: "")
            .contains(""""name": "Laptop"""")
            .contains(""""price": 1000.99""")

        val generatedExample = examplesCreated.first { it.name == "product_GET_200.json" }
        assertThat(generatedExample.readText()).contains(""""path": "/product"""")
    }
}