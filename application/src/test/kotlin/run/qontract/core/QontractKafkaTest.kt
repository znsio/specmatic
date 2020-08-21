package run.qontract.core

import org.apache.kafka.clients.consumer.ConsumerGroupMetadata
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.producer.Callback
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.Metric
import org.apache.kafka.common.MetricName
import org.apache.kafka.common.PartitionInfo
import org.apache.kafka.common.TopicPartition
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

internal open class FakeProducer : Producer<String, String> {
    override fun close() {
    }

    override fun close(timeout: Duration?) {
    }

    override fun initTransactions() {
    }

    override fun beginTransaction() {
    }

    override fun sendOffsetsToTransaction(offsets: MutableMap<TopicPartition, OffsetAndMetadata>?, consumerGroupId: String?) {
    }

    override fun sendOffsetsToTransaction(offsets: MutableMap<TopicPartition, OffsetAndMetadata>?, groupMetadata: ConsumerGroupMetadata?) {
    }

    override fun commitTransaction() {
    }

    override fun abortTransaction() {
    }

    override fun send(record: ProducerRecord<String, String>?): Future<RecordMetadata> {
        TODO("Not yet implemented")
    }

    override fun send(record: ProducerRecord<String, String>?, callback: Callback?): Future<RecordMetadata> {
        TODO("Not yet implemented")
    }

    override fun flush() {
    }

    override fun partitionsFor(topic: String?): MutableList<PartitionInfo> {
        TODO("Not yet implemented")
    }

    override fun metrics(): MutableMap<MetricName, out Metric> {
        TODO("Not yet implemented")
    }

}

open class FakeFutureRecordMetada : Future<RecordMetadata> {
    override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
        TODO("Not yet implemented")
    }

    override fun isCancelled(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isDone(): Boolean {
        TODO("Not yet implemented")
    }

    override fun get(): RecordMetadata {
        return RecordMetadata(TopicPartition("topic", 0), 0, 0, 0, 0, 0, 0)
    }

    override fun get(timeout: Long, unit: TimeUnit): RecordMetadata {
        TODO("Not yet implemented")
    }

}

internal class QontractKafkaTest {
    @Test
    fun `send method should create and send a producer record`() {
        val fakeKafkaInstance = object : KafkaInstance {
            override val bootstrapServers: String = ""

            override var portBindings: List<String>
                get() = emptyList()
                set(value) {}

            override fun close() {
            }
        }

        var topicReceived: String? = null
        var key: String? = null
        var message: String? = null

        val fakeProducerPredicate: CreateProducerPredicate = {
            object : FakeProducer() {
                override fun send(record: ProducerRecord<String, String>?): Future<RecordMetadata> {
                    topicReceived = record?.topic()
                    key = record?.key()
                    message = record?.value()

                    return FakeFutureRecordMetada()
                }
            }
        }

        QontractKafka(fakeKafkaInstance, fakeProducerPredicate).use { kafka ->
            kafka.send("test-topic", "data")
        }

        assertThat(topicReceived).isEqualTo("test-topic")
        assertThat(key).isNull()
        assertThat(message).isEqualTo("data")
    }

    @Test
    fun `send method should create and send a producer record with a key`() {
        val fakeKafkaInstance = object : KafkaInstance {
            override val bootstrapServers: String = ""

            override var portBindings: List<String>
                get() = emptyList()
                set(value) {}

            override fun close() {
            }
        }

        var topicReceived: String? = null
        var key: String? = null
        var message: String? = null

        val fakeProducerPredicate: CreateProducerPredicate = {
            object : FakeProducer() {
                override fun send(record: ProducerRecord<String, String>?): Future<RecordMetadata> {
                    topicReceived = record?.topic()
                    key = record?.key()
                    message = record?.value()

                    return FakeFutureRecordMetada()
                }
            }
        }

        QontractKafka(fakeKafkaInstance, fakeProducerPredicate).use { kafka ->
            kafka.send("test-topic", "some-key", "data")
        }

        assertThat(topicReceived).isEqualTo("test-topic")
        assertThat(key).isEqualTo("some-key")
        assertThat(message).isEqualTo("data")
    }
}