package io.specmatic.core

import io.specmatic.core.pattern.*
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.StringValue
import io.specmatic.core.value.Value
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WorkflowTest {
    val postScenario = Scenario(
        "Add data",
        HttpRequestPattern(
            method = "POST",
            httpPathPattern = buildHttpPathPattern("/data"),
            body = StringPattern()
        ),
        HttpResponsePattern(
            status = 201,
            body = JSONObjectPattern(
                mapOf(
                    "id" to StringPattern()
                )
            )
        ),
        emptyMap(),
        emptyList(),
        emptyMap(),
        emptyMap(),
    )

    val getScenario = Scenario(
        "Get data",
        HttpRequestPattern(
            method = "GET",
            httpPathPattern = buildHttpPathPattern("/data/(id:string)"),
            body = StringPattern()
        ),
        HttpResponsePattern(
            status = 200,
            body = JSONObjectPattern(
                mapOf(
                    "id" to StringPattern()
                )
            )
        ),
        emptyMap(),
        emptyList(),
        emptyMap(),
        emptyMap(),
    )

    @Test
    fun `should fetch the specified id out a response and add it to a request`() {
        val request = HttpRequest("POST", "/data/id-from-test-data", body = StringValue("data"))
        val response = HttpResponse(201, body = parsedJSONObject("""{"id": "id-from-response"}"""))

        val workflow = Workflow(
            workflow = WorkflowConfiguration(
                ids = mapOf(
                    "GET /data/(id:string) -> 200" to WorkflowIDOperation(use = "PATH.id"),
                    "POST /data -> 201" to WorkflowIDOperation(extract = "BODY.id")
                )
            )
        )

        workflow.extractDataFrom(response, postScenario)
        val updatedRequest = workflow.updateRequest(request, getScenario)

        assertThat(updatedRequest.path).isEqualTo("/data/id-from-response")

    }

    @Test
    fun `should fetch the specified id out a response and add it to a request when configured for all requests`() {
        val request = HttpRequest("POST", "/data/id-from-test-data", body = StringValue("data"))
        val response = HttpResponse(201, body = parsedJSONObject("""{"id": "id-from-response"}"""))

        val workflow = Workflow(
            workflow = WorkflowConfiguration(
                ids = mapOf(
                    "*" to WorkflowIDOperation(use = "PATH.id"),
                    "POST /data -> 201" to WorkflowIDOperation(extract = "BODY.id")
                )
            )
        )

        workflow.extractDataFrom(response, postScenario)
        val updatedRequest = workflow.updateRequest(request, getScenario)

        assertThat(updatedRequest.path).isEqualTo("/data/id-from-response")

    }
}