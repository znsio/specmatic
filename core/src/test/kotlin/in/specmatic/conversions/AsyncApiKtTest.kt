package `in`.specmatic.conversions

import `in`.specmatic.core.*
import `in`.specmatic.core.log.Verbose
import `in`.specmatic.core.log.logger
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.value.Value
import `in`.specmatic.stub.HttpStub
import `in`.specmatic.stub.createStubFromContracts
import `in`.specmatic.test.TestExecutor
import com.fasterxml.jackson.annotation.JsonProperty
import `in`.specmatic.stub.JMSStub
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Ignore
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import java.net.URI
import java.util.function.Consumer
import java.util.regex.Pattern


internal class AsyncApiKtTest {
    companion object {
        val openAPISpec = """
Feature: Messages

Background:
  Given asyncapi asyncapi/messages.yaml            
        """.trimIndent()

        private val sourceSpecPath = File("src/test/resources/hello.spec").canonicalPath

        @BeforeAll
        @JvmStatic
        fun setup() {
            logger = Verbose()
        }
    }

    @Test
    fun `should generate stub with non primitive open api data types`() {
        val feature = parseGherkinStringToFeature(
            """
Feature: Messages

Background:
  Given asyncapi asyncapi/messages.yaml
        """.trimIndent(), sourceSpecPath
        )

        val response = JMSStub(feature)
    }

}