package application

import `in`.specmatic.reports.CentralContractRepoReportJson
import `in`.specmatic.reports.SpecificationOperation
import `in`.specmatic.reports.SpecificationRow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.io.File

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [SpecmaticApplication::class, CentralContractRepoReportCommand::class]
)
class CentralContractRepoReportCommandTestE2E {

    @Autowired
    lateinit var centralContractRepoReportCommand: CentralContractRepoReportCommand

    @Test
    fun `test generates report json file`() {
        centralContractRepoReportCommand.call()
        val reportJson: CentralContractRepoReportJson = Json.decodeFromString(reportFile.readText())
        assertThat(reportJson).isEqualTo(
            CentralContractRepoReportJson(
                listOf(
                    SpecificationRow(
                        "specifications/service1/service1.yaml",
                        "HTTP",
                        listOf(
                            SpecificationOperation(
                                "/hello/{id}",
                                "GET",
                                200
                            ),
                            SpecificationOperation(
                                "/hello/{id}",
                                "GET",
                                404
                            ),
                            SpecificationOperation(
                                "/hello/{id}",
                                "GET",
                                400
                            )
                        )
                    )
                )
            )
        )
    }

    companion object {
        private val reportFile = File("./build/reports/specmatic/central_contract_repo_report.json")

        @JvmStatic
        @BeforeAll
        fun setupBeforeAll() {
            createSpecFiles()
        }

        @JvmStatic
        @AfterAll
        fun tearDownAfterAll() {
            File("./specifications").deleteRecursively()
            reportFile.delete()
        }

        private fun createSpecFiles() {
            val service1spec = """
openapi: 3.0.0
info:
  title: Sample API
  description: Optional multiline or single-line description in [CommonMark](http://commonmark.org/help/) or HTML.
  version: 0.1.9
servers:
  - url: http://api.example.com/v1
    description: Optional server description, e.g. Main (production) server
  - url: http://staging-api.example.com
    description: Optional server description, e.g. Internal staging server for testing
paths:
  /hello/{id}:
    get:
      summary: hello world
      description: Optional extended description in CommonMark or HTML.
      parameters:
        - in: path
          name: id
          schema:
            type: integer
          required: true
          description: Numeric ID
      responses:
        '200':
          description: Says hello
          content:
            application/json:
              schema:
                type: string
        '404':
          description: Not Found
          content:
            application/json:
              schema:
                type: string
        '400':
          description: Bad Request
          content:
            application/json:
              schema:
                type: string
        """
            val service1File = File("./specifications/service1/service1.yaml")
            service1File.parentFile.mkdirs()
            service1File.writeText(service1spec)
        }
    }
}