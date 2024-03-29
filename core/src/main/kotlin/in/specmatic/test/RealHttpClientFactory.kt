package `in`.specmatic.test

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.*
import org.apache.http.conn.ssl.NoopHostnameVerifier
import org.apache.http.conn.ssl.TrustAllStrategy
import org.apache.http.ssl.SSLContextBuilder

object RealHttpClientFactory: HttpClientFactory {
    override fun create(timeout: Int): HttpClient = HttpClient(Apache) {
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
            requestTimeoutMillis = (timeout * 1000).toLong()
        }
    }
}