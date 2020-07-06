package run.qontract.stub

import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import run.qontract.core.Feature
import run.qontract.core.value.KafkaMessage
import run.qontract.mock.ScenarioStub

fun stubKafkaContracts(kafkaStubs: List<KafkaStubData>, bootstrapServers: String, createTopics: (List<String>, String) -> Unit, createProducer: (String) -> Producer<String, String>) {
    try { createTopics(kafkaStubs.map { it.kafkaMessage.topic }, bootstrapServers) } catch(e: Throwable) { }

    createProducer(bootstrapServers).use { producer ->
        for(stub in kafkaStubs) {
            val producerRecord = producerRecord(stub.kafkaMessage)
            val future = producer.send(producerRecord)
            future.get()
        }
    }
}

fun producerRecord(kafkaMessage: KafkaMessage): ProducerRecord<String, String> {
    return when(val key = kafkaMessage.key) {
        null -> ProducerRecord(kafkaMessage.topic, kafkaMessage.value.toStringValue())
        else -> ProducerRecord(kafkaMessage.topic, key.toStringValue(), kafkaMessage.value.toStringValue())
    }
}

fun contractInfoToKafkaExpectations(contractInfo: List<Pair<Feature, List<ScenarioStub>>>): List<KafkaStubData> {
    return contractInfo.flatMap { (_, mocks) ->
        mocks.mapNotNull { it.kafkaMessage }.fold(emptyList<KafkaStubData>()) { innerStubs, kafkaMessage ->
            innerStubs.plus(KafkaStubData(kafkaMessage))
        }
    }
}
