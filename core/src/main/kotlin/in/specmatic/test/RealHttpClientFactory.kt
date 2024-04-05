package `in`.specmatic.test

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.*
import org.apache.http.conn.ssl.NoopHostnameVerifier
import org.apache.http.conn.ssl.TrustAllStrategy
import org.apache.http.ssl.SSLContextBuilder

const val BREATHING_ROOM_FOR_REQUEST_TIMEOUT_TO_KICK_IN_FIRST = 1

class RealHttpClientFactory(val timeoutPolicy: TimeoutPolicy): HttpClientFactory {
    constructor(timeout: Int): this(TimeoutPolicy(timeout))

    override fun create(): HttpClient = HttpClient(Apache) {
        expectSuccess = false

        followRedirects = false

        engine {
            customizeClient {
                setSSLContext(
                    SSLContextBuilder.create()
                        .loadTrustMaterial(TrustAllStrategy())
                        .build()
                )
                setSSLHostnameVerifier(NoopHostnameVerifier())
            }
        }

        install(HttpTimeout) {
            timeoutPolicy.configure(this)
        }
    }
}
