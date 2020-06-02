package run.qontract.stub

import org.apache.kafka.clients.producer.ProducerRecord
import run.qontract.core.*
import run.qontract.core.value.KafkaMessage
import run.qontract.mock.ScenarioStub
import run.qontract.nullLog

fun stubKafkaContracts(kafkaStubs: List<KafkaStubData>, bootstrapServers: String) {
    createConsumer(bootstrapServers, false).use {
        createTopics(kafkaStubs.map { it.kafkaMessage.topic }, bootstrapServers)
    }

    createProducer(bootstrapServers).use { producer ->
        for(stub in kafkaStubs) {
            val producerRecord = producerRecord(stub.kafkaMessage)
            val future = producer.send(producerRecord)
            future.get()
        }
    }
}

fun producerRecord(kafkaMessage: KafkaMessage): ProducerRecord<String, String> {
    val key = kafkaMessage.key
    return if (key != null) ProducerRecord(kafkaMessage.topic, key.toStringValue(), kafkaMessage.value.toStringValue()) else ProducerRecord(kafkaMessage.topic, kafkaMessage.value.toStringValue())
}

fun contractInfoToKafkaExpectations(contractInfo: List<Pair<Feature, List<ScenarioStub>>>): List<KafkaStubData> {
    return contractInfo.flatMap { (_, mocks) ->
        mocks.mapNotNull { it.kafkaMessage }.fold(emptyList<KafkaStubData>()) { innerStubs, kafkaMessage ->
            innerStubs.plus(KafkaStubData(kafkaMessage))
        }
    }
}
