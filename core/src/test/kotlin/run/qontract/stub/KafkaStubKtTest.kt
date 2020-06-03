package run.qontract.stub

import io.mockk.every
import io.mockk.mockk
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.Feature
import run.qontract.core.value.KafkaMessage
import run.qontract.core.value.StringValue
import run.qontract.mock.ScenarioStub
import java.util.concurrent.Future

internal class KafkaStubKtTest {
    @Test
    fun `creating a producer record with a key`() {
        val record = producerRecord(KafkaMessage("customers", key = StringValue("csv"), value = StringValue("data")))
        assertThat(record.topic()).isEqualTo("customers")
        assertThat(record.key()).isEqualTo("csv")
        assertThat(record.value()).isEqualTo("data")
    }

    @Test
    fun `creating a producer record without a key`() {
        val record = producerRecord(KafkaMessage("customers", value = StringValue("data")))
        assertThat(record.topic()).isEqualTo("customers")
        assertThat(record.value()).isEqualTo("data")
    }

    @Test
    fun `create the specified topic and produce the specified messages`() {
        val expectedBootstrapServers = "servers"

        val createTopics: (List<String>, String) -> Unit = { topics, bootstrapServers ->
            assertThat(topics.single()).isEqualTo("customer")
            assertThat(bootstrapServers).isEqualTo(expectedBootstrapServers)
        }

        val createProducer: (String) -> Producer<String, String> = { bootstrapServers ->
            assertThat(bootstrapServers).isEqualTo(expectedBootstrapServers)
            val future = mockk<Future<RecordMetadata>>().also {
                every { it.get() } returns null
            }

            mockk<Producer<String, String>>().also {
                every { it.send(ProducerRecord("customer", "csv", "data"))} returns future
                every { it.close() } returns Unit
            }
        }

        stubKafkaContracts(listOf(KafkaStubData(KafkaMessage(topic = "customer", key = StringValue("csv"), value = StringValue("data")))), expectedBootstrapServers, createTopics, createProducer)
    }

    @Test
    fun `creating Kafka message expectations`() {
        val feature = Feature("""Feature: Kafka Qontract
  Scenario: New message
    * kafka-message order (string)""".trimMargin())

        val kafkaMessage = KafkaMessage("order", value = StringValue("data"))
        val expectations = contractInfoToKafkaExpectations(listOf(Pair(feature, listOf(ScenarioStub(kafkaMessage = kafkaMessage)))))
        assertThat(expectations.single().kafkaMessage).isEqualTo(kafkaMessage)
    }
}