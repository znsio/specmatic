package application

import io.specmatic.core.lifecycle.AfterLoadingStaticExamples
import io.specmatic.core.lifecycle.LifecycleHooks
import io.specmatic.core.log.logger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import picocli.CommandLine
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
        assertThat(output).contains("No matching specification found for this example")
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

    @Nested
    inner class ValidateTests {
        private val specFile = File("src/test/resources/specifications/simpleSpec/spec.yaml")

        @Test
        fun `should validate both inline and external examples by default`() {
            val command = ExamplesCommand.Validate().also { it.contractFile = specFile }
            val (stdOut, exitCode) = captureStandardOutput { command.call() }
            println(stdOut)

            assertThat(exitCode).isEqualTo(0)
            assertThat(stdOut).containsIgnoringWhitespaces("""
            =============== Inline Example Validation Summary ===============
            All 1 example(s) are valid.
            =================================================================  
            """.trimIndent())
            assertThat(stdOut).containsIgnoringWhitespaces("""
            =============== Example File Validation Summary ===============
            All 1 example(s) are valid.
            ===============================================================
            """.trimIndent())
        }

        @Test
        fun `should validate inline only when the examplesToValidate flag is set to inline`() {
            val command = ExamplesCommand.Validate().also {
                it.contractFile = specFile
                it.examplesToValidate = ExamplesCommand.Validate.ExamplesToValidate.INLINE
            }
            val (stdOut, exitCode) = captureStandardOutput { command.call() }
            println(stdOut)

            assertThat(exitCode).isEqualTo(0)
            assertThat(stdOut).containsIgnoringWhitespaces("""
            =============== Inline Example Validation Summary ===============
            All 1 example(s) are valid.
            =================================================================
            """.trimIndent())
            assertThat(stdOut).doesNotContain("""
            =============== Example File Validation Summary ===============
            """.trimIndent())
        }

        @Test
        fun `should validate external only when the examplesToValidate flag is set to external`() {
            val command = ExamplesCommand.Validate().also {
                it.contractFile = specFile
                it.examplesToValidate = ExamplesCommand.Validate.ExamplesToValidate.EXTERNAL
            }
            val (stdOut, exitCode) = captureStandardOutput { command.call() }
            println(stdOut)

            assertThat(exitCode).isEqualTo(0)
            assertThat(stdOut).containsIgnoringWhitespaces("""
            =============== Example File Validation Summary ===============
            All 1 example(s) are valid.
            ===============================================================
            """.trimIndent())
            assertThat(stdOut).doesNotContain("""
            =============== Inline Example Validation Summary =============
            """.trimIndent())
        }

        @ParameterizedTest
        @CsvSource(
            "inline, INLINE, InLine, INLINE",
            "external, EXTERNAL, External, EXTERNAL",
            "both, BOTH, Both, BOTH"
        )
        fun `should convert to examplesToValidate enum ignoring case`(
            lowerCase: String, upperCase: String, titleCase: String, expected: String
        ) {
            val cases = listOf(lowerCase, upperCase, titleCase)
            assertThat(cases).allSatisfy {
                val examplesToValidate = ExamplesCommand.Validate.ExamplesToValidateConverter().convert(it)
                println("$it -> $examplesToValidate")
                assertThat(examplesToValidate.name).isEqualTo(expected)
            }
        }

        @Test
        fun `should not result in exit code 1 when example folder is empty`(@TempDir tempDir: File) {
            val copiedSpecFile = specFile.copyTo(tempDir.resolve("spec.yaml"))
            val examplesDir = tempDir.resolve("spec_examples").also { it.mkdirs() }

            val command = ExamplesCommand.Validate().also {
                it.contractFile = copiedSpecFile
                it.examplesToValidate = ExamplesCommand.Validate.ExamplesToValidate.EXTERNAL
            }
            val (stdOut, exitCode) = captureStandardOutput { command.call() }
            println(stdOut)

            assertThat(exitCode).isEqualTo(0)
            assertThat(stdOut).containsIgnoringWhitespaces("""
            No example files found in $examplesDir
            """.trimIndent())
        }
    }

    @Nested
    inner class ValidateLifeCycleTests {
        private val hook = AfterLoadingStaticExamples { examplesUsedFor, examples ->
            logger.log("life cycle hook called for '$examplesUsedFor'")
            examples.forEach { (feature, stubs) ->
                logger.log("spec: '${File(feature.path).name}'")
                logger.log("implicit example: '${feature.stubsFromExamples.map { (k, _) -> k }.sorted().joinToString(",")}'")
                logger.log("external stub: '${stubs.map { File(it.filePath!!).name }.sorted().joinToString(",") }'")
            }
        }

        @BeforeEach
        fun setupHook() {
            LifecycleHooks.afterLoadingStaticExamples.register(hook)
        }

        @AfterEach
        fun tearDownHook() {
            LifecycleHooks.afterLoadingStaticExamples.remove(hook)
        }

        private val cli = CommandLine(ExamplesCommand.Validate(), CommandLine.defaultFactory())

        @Test
        fun `should call the life cycle hook for validate if only spec file is provided`() {
            val (stdOut, exitCode) = captureStandardOutput {
                cli.execute("--spec-file", "src/test/resources/examples/single/persons.yaml")
            }

            assertThat(exitCode).isEqualTo(0)
            assertThat(stdOut).containsIgnoringWhitespaces("""
            life cycle hook called for 'Validation'
            spec: 'persons.yaml'
            implicit example: 'person-example-01,person-example-11'
            external stub: 'create_person-01.json,create_person-02.json'
            """.trimIndent())
        }

        @Test
        fun `should call the life cycle hook for validate spec file and example file is provided`() {
            val (stdOut, exitCode) = captureStandardOutput {
                cli.execute(
                    "--spec-file", "src/test/resources/examples/only_specs/persons/persons.yaml",
                    "--example-file", "src/test/resources/examples/only_examples/persons/persons_examples/create_person-01.json"
                )
            }

            assertThat(exitCode).isEqualTo(0)
            assertThat(stdOut).containsIgnoringWhitespaces("""
            life cycle hook called for 'Validation'
            spec: 'persons.yaml'
            implicit example: 'person-example-01,person-example-11'
            external stub: 'create_person-01.json'
            """.trimIndent())
        }

        @Test
        fun `should call the life cycle hook for validate if both spec file and examples dir is provided`() {
            val (stdOut, exitCode) = captureStandardOutput {
                val non_implicit_examples_dir = "src/test/resources/examples/only_examples/persons/persons_examples"
                cli.execute(
                    "--spec-file", "src/test/resources/examples/only_specs/persons/persons.yaml",
                    "--examples-dir", non_implicit_examples_dir
                )
            }

            assertThat(exitCode).isEqualTo(0)
            assertThat(stdOut).containsIgnoringWhitespaces("""
            life cycle hook called for 'Validation'
            spec: 'persons.yaml'
            implicit example: 'person-example-01,person-example-11'
            external stub: 'create_person-01.json,create_person-02.json'
            """.trimIndent())
        }

        @Test
        fun `should call the life cycle hook for validate if spec dir is provided`() {
            val (stdOut, exitCode) = captureStandardOutput {
                cli.execute("--specs-dir", "src/test/resources/examples/multiple")
            }

            assertThat(exitCode).isEqualTo(0)
            assertThat(stdOut).containsIgnoringWhitespaces("""
            life cycle hook called for 'Validation'
            spec: 'persons.yaml'
            implicit example: 'person-example-01,person-example-11'
            external stub: 'create_person-01.json,create_person-02.json'
            """.trimIndent())
            assertThat(stdOut).containsIgnoringWhitespaces("""
            life cycle hook called for 'Validation'
            spec: 'spec.yaml'
            implicit example: 'CreateProduct'
            external stub: 'example_1.json,example_3.json'
            """.trimIndent())
        }

        @Test
        fun `should call the life cycle hook for validate if both spec dir and examples dir is provided`() {
            val (stdOut, exitCode) = captureStandardOutput {
                cli.execute(
                    "--specs-dir", "src/test/resources/examples/only_specs",
                    "--examples-base-dir", "src/test/resources/examples/only_examples")
            }

            assertThat(exitCode).isEqualTo(0)
            assertThat(stdOut).containsIgnoringWhitespaces("""
            life cycle hook called for 'Validation'
            spec: 'persons.yaml'
            implicit example: 'person-example-01,person-example-11'
            external stub: 'create_person-01.json,create_person-02.json'
            """.trimIndent())
            assertThat(stdOut).containsIgnoringWhitespaces("""
            life cycle hook called for 'Validation'
            spec: 'spec.yaml'
            implicit example: 'CreateProduct'
            external stub: 'example_1.json,example_3.json'
            """.trimIndent())
        }
    }
}