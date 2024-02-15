package `in`.specmatic.core.utilities

import `in`.specmatic.core.DEFAULT_WORKING_DIRECTORY
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.File
import java.net.ServerSocket

class WebSourceTest {
    @Test
    fun `test downloading from a web source`() {
        val port: Int = ServerSocket(0).use { it.localPort }

        val server = embeddedServer(Netty, port = port) {
            routing {
                get("/spec.yaml") {
                    call.respondText("data")
                }
            }
        }.start(wait = false)


        try {
            val specificationOnTheWeb = "http://localhost:$port/spec.yaml"

            val webSource = WebSource(
                listOf(),
                listOf(specificationOnTheWeb)
            )

            val contractData = webSource.loadContracts({ source -> source.stubContracts }, DEFAULT_WORKING_DIRECTORY, "")

            assertThat(contractData).isNotEmpty
            assertThat(contractData).allSatisfy {
                val data = File(it.path).readText()
                assertThat(data).isEqualTo("data")
            }
        } finally {
            server.stop(0, 0)
        }
    }

    @AfterEach
    fun tearDown() {
        File(DEFAULT_WORKING_DIRECTORY).deleteRecursively()
    }
}