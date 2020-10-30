package run.qontract.stub

import run.qontract.core.HttpRequestPattern
import run.qontract.core.HttpResponse
import run.qontract.core.Resolver
import run.qontract.core.value.KafkaMessage

interface StubData

data class HttpStubData(val requestType: HttpRequestPattern, val response: HttpResponse, val resolver: Resolver, val delay: Long? = null) : StubData {
    fun softCastResponseToXML(): HttpStubData =
            this.copy(response = response.copy(body = softCastValueToXML(response.body)))
}

data class KafkaStubData(val kafkaMessage: KafkaMessage) : StubData

data class StubDataItems(val http: List<HttpStubData> = emptyList(), val kafka: List<KafkaStubData> = emptyList())
