package `in`.specmatic.stub

import `in`.specmatic.core.HttpRequestPattern
import `in`.specmatic.core.HttpResponse
import `in`.specmatic.core.Resolver
import `in`.specmatic.core.value.KafkaMessage

interface StubData

data class HttpStubData(val requestType: HttpRequestPattern, val response: HttpResponse, val resolver: Resolver, val delayInSeconds: Int? = null) : StubData {
    fun softCastResponseToXML(): HttpStubData =
            this.copy(response = response.copy(body = softCastValueToXML(response.body)))
}

data class KafkaStubData(val kafkaMessage: KafkaMessage) : StubData

data class StubDataItems(val http: List<HttpStubData> = emptyList(), val kafka: List<KafkaStubData> = emptyList())
