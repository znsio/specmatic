package io.specmatic.stub

import io.mockk.every
import io.mockk.mockk
import io.specmatic.core.Feature
import io.specmatic.core.utilities.portIsInUse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.util.stream.Stream

class BaseURLTest {

    @Test
    fun `default should form a baseUrl using default scheme, host and port`() {
        val baseUrl = BaseURL.default()
        assertThat(baseUrl.value).isEqualTo("http://0.0.0.0:9000")
    }

    @Test
    fun `should pick a random free port if default port is already in use`() {
        ServerSocket(9000, 1, InetAddress.getByName("0.0.0.0")).use {
            val baseUrl = BaseURL.default()
            val parsedUrl = URI(baseUrl.value)

            assertThat(parsedUrl.scheme).isEqualTo("http")
            assertThat(parsedUrl.host).isEqualTo("0.0.0.0")
            assertThat(parsedUrl.port).isNotEqualTo(9000)
            assertThat(portIsInUse(parsedUrl.host, parsedUrl.port)).isFalse()
        }
    }

    @Test
    fun `should not try to access preferred BaseURL from feature if value is not default`() {
        val baseUrl = BaseURL.from("http://localhost:3000")
        val feature = mockk<Feature> {
            every { path } returns "./api.yaml"
            every { getPreferredServer() } throws Exception("""
            Should not access preferred server if a non-default baseUrl is provided,
            This avoids issues with serverUrlIndex being global across all features,
            If a non-default baseUrl is available, it must take priority
            """.trimIndent())
        }

        val resolved = assertDoesNotThrow { baseUrl.getBaseUrlFor(feature) }
        assertThat(resolved).isEqualTo(baseUrl.value)
    }

    @ParameterizedTest
    @MethodSource("baseUrlResolveValuesProvider")
    fun `should log which baseUrl was chosen along with source and feature path during resolve`(
        baseUrl: BaseURL, feature: Feature, specToBaseUrlMap: Map<String, String?>, expectedLog: String
    ) {
        val (stdOut, _) = captureStandardOutput { baseUrl.getBaseUrlFor(feature, specToBaseUrlMap) }
        assertThat(stdOut).isEqualToNormalizingWhitespace(expectedLog)
    }

    companion object {
        @JvmStatic
        fun baseUrlResolveValuesProvider(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(
                    BaseURL.default(),
                    featureWithServers("http://localhost:5000", "http://localhost:4000"),
                    mapOf("./api.yaml" to "http://localhost:3000"),
                    "Using base URL: \"http://localhost:3000\" from Specmatic Config for OpenAPI Specification at \"./api.yaml\""
                ),
                Arguments.of(
                    BaseURL.from("http://localhost:5000"),
                    featureWithServers("http://localhost:6000", "http://localhost:9000"),
                    mapOf("./api.yaml" to "http://localhost:8080"),
                    "Using base URL: \"http://localhost:8080\" from Specmatic Config for OpenAPI Specification at \"./api.yaml\""
                ),
                Arguments.of(
                    BaseURL.from("http://localhost:3000"),
                    featureWithServers("http://localhost:5000", "http://localhost:4000"),
                    emptyMap<String, String?>(),
                    "Using base URL: \"http://localhost:3000\" from Arguments for OpenAPI Specification at \"./api.yaml\""
                ),
                Arguments.of(
                    BaseURL.default(),
                    featureWithServers("http://localhost:5000", "http://localhost:4000"),
                    emptyMap<String, String?>(),
                    "Using base URL: \"http://localhost:5000\" from OpenAPI servers for OpenAPI Specification at \"./api.yaml\""
                ),
                Arguments.of(
                    BaseURL.default(),
                    featureWithServers(),
                    emptyMap<String, String?>(),
                    "Using base URL: \"http://0.0.0.0:9000\" from Default BaseURL for OpenAPI Specification at \"./api.yaml\""
                )
            )
        }

        private fun featureWithServers(vararg servers: String): Feature {
            return Feature(name = "", servers = servers.toList(), path = "./api.yaml")
        }
    }
}
