package `in`.specmatic.conversions

import `in`.specmatic.core.*
import `in`.specmatic.core.pattern.*
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.NumberValue
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.core.value.Value
import `in`.specmatic.mock.NoMatchingScenario
import `in`.specmatic.stub.*
import `in`.specmatic.test.TestExecutor
import io.ktor.util.reflect.*
import io.swagger.util.Yaml
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.Ignore
import org.junit.jupiter.api.*
import org.junit.jupiter.api.io.TempDir
import java.io.File

private const val ASYNC_API_FILE = "asyncApiTest.yaml"

class AsyncApiSpecificationTest {
    @BeforeEach
    fun `setup`() {
       val asyncApi = """
asyncapi: '2.4.0'
info:
  title: Email
  version: '1.0.0'
  description: Send Email
  license:
    name: Apache 2.0
    url: https://www.apache.org/licenses/LICENSE-2.0

servers:
  local:
    url: localhost:61616
    protocol: jms
    description: Test broker

defaultContentType: application/json

channels:
  mailbox:
    description: The topic on which measured values may be produced and consumed.
    subscribe:
      operationId: receiveEmail
      message:
        ${"$"}ref: '#/components/messages/email'

components:
  messages:
    email:
      name: email
      title: Email
      payload:
        ${"$"}ref: '#/components/schemas/emailPayload'

  schemas:
    emailPayload:
      type: object
      parameters:
        to:
          type: string
        body:
          type: string
       """.trimIndent()

        val asyncApiFile = File(ASYNC_API_FILE)
        asyncApiFile.createNewFile()
        asyncApiFile.writeText(asyncApi)
    }

    @AfterEach
    fun `teardown`() {
        File(ASYNC_API_FILE).delete()
    }

    @Test
    fun `should parse AsyncApi file`() {
        AsyncApiSpecification(ASYNC_API_FILE).toFeature()
    }
}