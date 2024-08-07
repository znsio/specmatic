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
                    "id" to NumberPattern()
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
            httpPathPattern = buildHttpPathPattern("/data/(id:number)"),
            body = StringPattern()
        ),
        HttpResponsePattern(
            status = 200,
            body = JSONObjectPattern(
                mapOf(
                    "id" to NumberPattern()
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
        val request = HttpRequest("POST", "/data/1", body = StringValue("data"))
        val response = HttpResponse(201, body = parsedJSONObject("""{"id": "1000"}"""))

        val workflow = Workflow(
            workflow = WorkflowConfiguration(
                ids = mapOf(
                    "GET /data/(id:number) -> 200" to WorkflowIDOperation(use = "PATH.id"),
                    "POST /data -> 201" to WorkflowIDOperation(extract = "BODY.id")
                )
            )
        )

        workflow.extractDataFrom(response, postScenario)
        val updatedRequest = workflow.updateRequest(request, getScenario)

        assertThat(updatedRequest.path).isEqualTo("/data/1000")

    }

    @Test
    fun `should fetch the specified id out a response and add it to a request when configured for all requests`() {
        val request = HttpRequest("POST", "/data/1", body = StringValue("data"))
        val response = HttpResponse(201, body = parsedJSONObject("""{"id": "1000"}"""))

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

        assertThat(updatedRequest.path).isEqualTo("/data/1000")

    }
}