package io.specmatic.core

import io.specmatic.core.value.NullValue
import io.specmatic.core.value.StringValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.function.Consumer

internal class ResultKtTest {
    @Test
    fun `add response body to result variables`() {
        val result = Result.Success().withBindings(mapOf("data" to "response-body"), HttpResponse.ok("10")) as Result.Success
        assertThat(result.variables).isEqualTo(mapOf("data" to "10"))
    }

    @Test
    fun `result report for failure with multiple causes`() {
        val result = Result.Failure(
            causes = listOf(
                Result.FailureCause("", cause = Result.Failure("Failure 1", breadCrumb = "id")),
                Result.FailureCause("", cause = Result.Failure("Failure 2", breadCrumb = "height"))
            ), breadCrumb = "person"
        )

        assertThat(result.reportString()).satisfies(Consumer {
            assertThat(it).contains("person.id")
            assertThat(it).contains("person.height")
            assertThat(it).contains("Failure 1")
            assertThat(it).contains("Failure 2")

            println(result.reportString())
        })
    }

    @Test
    fun `mismatch for null`() {
        val result = valueError(NullValue)
        assertThat(result).isEqualTo("null")
    }

    @Test
    fun `mismatch for non-null`() {
        val result = valueError(StringValue("test"))
        assertThat(result).isEqualTo("\"test\"")
    }
}