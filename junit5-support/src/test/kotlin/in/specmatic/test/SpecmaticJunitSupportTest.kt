package `in`.specmatic.test

import `in`.specmatic.core.TestConfig
import `in`.specmatic.test.SpecmaticJUnitSupport.Companion.HOST
import `in`.specmatic.test.SpecmaticJUnitSupport.Companion.PORT
import `in`.specmatic.test.SpecmaticJUnitSupport.Companion.PROTOCOL
import `in`.specmatic.test.SpecmaticJUnitSupport.Companion.TEST_BASE_URL
import `in`.specmatic.test.reports.coverage.Endpoint
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.opentest4j.TestAbortedException

class SpecmaticJunitSupportTest {
    @Test
    fun `should retain open api path parameter convention for parameterized endpoints`() {
        val result: Pair<List<ContractTest>, List<Endpoint>> = SpecmaticJUnitSupport().loadTestScenarios(
            "./src/test/resources/spec_with_parameterized_paths.yaml",
            "",
            "",
            TestConfig(emptyMap(), emptyMap()),
            filterName = null,
            filterNotName = null
        )
        val specEndpoints = result.second
        assertThat(specEndpoints.count()).isEqualTo(2)
        assertThat(specEndpoints.all { it.path == "/sayHello/{name}" })
    }

    @Test
    fun `should pick up and use testBaseURL system property when set`() {
        val url = "http://test.com"
        System.setProperty(TEST_BASE_URL, url)
        assertThat(SpecmaticJUnitSupport().constructTestBaseURL()).isEqualTo(url)
    }

    @Test
    fun `should pick up host and port when testBaseURL system property is not set`() {
        val domain = "test.com"
        val port = "8080"
        System.setProperty(HOST, domain)
        System.setProperty(PORT, port)
        assertThat(SpecmaticJUnitSupport().constructTestBaseURL()).isEqualTo("http://$domain:$port")
    }

    @Test
    fun `should take the domain from the host system property when it is an URI`() {
        val domainName = "test.com"
        val domain = "http://$domainName"
        val port = "8080"
        System.setProperty(HOST, domain)
        System.setProperty(PORT, port)
        assertThat(SpecmaticJUnitSupport().constructTestBaseURL()).isEqualTo("http://$domainName:$port")
    }

    @Test
    fun `should pick use protocol when system property is set`() {
        val protocol = "https"
        val domain = "test.com"
        val port = "8080"
        System.setProperty(HOST, domain)
        System.setProperty(PORT, port)
        System.setProperty(PROTOCOL, protocol)
        assertThat(SpecmaticJUnitSupport().constructTestBaseURL()).isEqualTo("$protocol://$domain:$port")
    }

    @Test
    fun `testBaseURL system property should be valid URI`() {
        System.setProperty(TEST_BASE_URL, "http://invalid url.com")
        val ex = assertThrows<TestAbortedException> {
            SpecmaticJUnitSupport().constructTestBaseURL()
        }
        assertThat(ex.message).isEqualTo("Please specific a valid URL in $TEST_BASE_URL environment variable")
    }

    @Test
    fun `host system property should be valid`() {
        System.setProperty(PROTOCOL, "https")
        System.setProperty(HOST, "invalid domain")
        System.setProperty(PORT, "8080")
        val ex = assertThrows<TestAbortedException> {
            SpecmaticJUnitSupport().constructTestBaseURL()
        }
        assertThat(ex.message).isEqualTo("Please specific a valid $PROTOCOL, $HOST and $PORT environment variables")
    }

    @Test
    fun `protocol system property should be valid`() {
        System.setProperty(PROTOCOL, "invalid")
        System.setProperty(HOST, "test.com")
        System.setProperty(PORT, "8080")
        val ex = assertThrows<TestAbortedException> {
            SpecmaticJUnitSupport().constructTestBaseURL()
        }
        assertThat(ex.message).isEqualTo("Please specific a valid $PROTOCOL, $HOST and $PORT environment variables")
    }

    @Test
    fun `port system property should be valid`() {
        System.setProperty(PROTOCOL, "https")
        System.setProperty(HOST, "test.com")
        System.setProperty(PORT, "invalid_port")
        val ex = assertThrows<TestAbortedException> {
            SpecmaticJUnitSupport().constructTestBaseURL()
        }
        assertThat(ex.message).isEqualTo("Please specific a valid $PROTOCOL, $HOST and $PORT environment variables")
    }

    @Test
    fun `testBaseURL or host and port system property are mandatory`() {
        val ex = assertThrows<TestAbortedException> {
            SpecmaticJUnitSupport().constructTestBaseURL()
        }
        assertThat(ex.message).isEqualTo("Please specific $TEST_BASE_URL OR host and port as environment variables")
    }

    @AfterEach
    fun tearDown() {
        listOf(TEST_BASE_URL, HOST, PORT, PROTOCOL).forEach { System.clearProperty(it) }
    }
}