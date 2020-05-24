package run.qontract.core

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.testcontainers.containers.KafkaContainer
import run.qontract.core.pattern.parsedValue
import run.qontract.core.value.KafkaMessage
import run.qontract.core.value.StringValue
import java.io.Closeable
import java.time.Duration
import java.util.*

class QontractKafka(kafkaPort: Int = 9093) : Closeable {
    private val kafkaContainer = KafkaContainer()

    val bootstrapServers
        get() = kafkaContainer.bootstrapServers

    fun setupTopic(topic: String) = setupTopics(listOf(topic))

    fun setupTopics(topics: List<String>) {
        createTopics(topics, kafkaContainer.bootstrapServers)
    }

    fun send(topic: String, key: String, message: String) {
        createProducer(kafkaContainer.bootstrapServers).use { producer ->
            val producerRecord = ProducerRecord<String, String>(topic, key, message)
            val future = producer.send(producerRecord)
            future.get()
        }
    }

    fun send(topic: String, message: String) {
        createProducer(kafkaContainer.bootstrapServers).use { producer ->
            val producerRecord = ProducerRecord<String, String>(topic, message)
            val future = producer.send(producerRecord)
            future.get()
        }
    }

    fun fetch(topic: String): List<KafkaMessage> {
        return createConsumer(kafkaContainer.bootstrapServers, false).use { consumer ->
            consumer.subscribe(listOf(topic))
            consumer.poll(Duration.ofSeconds(1))
            consumer.seekToBeginning(listOf(TopicPartition(topic, 0)))
            val messages = consumer.poll(Duration.ofSeconds(1))
            messages.map {
                KafkaMessage(topic, it.key()?.let { key -> StringValue(key) }, parsedValue(it.value()))
            }
        }
    }

    init {
        kafkaContainer.portBindings = listOf("$kafkaPort:9093")
        kafkaContainer.start()
    }

    override fun close() {
        kafkaContainer.close()
    }
}

fun createConsumer(brokers: String, commit: Boolean): Consumer<String, String> {
    val props = Properties()
    props["bootstrap.servers"] = brokers
    props["group.id"] = "qontract"
    props["key.deserializer"] = StringDeserializer::class.java
    props["value.deserializer"] = StringDeserializer::class.java
    props[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"

    if(!commit)
        props[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = "false"

    return KafkaConsumer<String, String>(props)
}

fun createProducer(brokers: String): Producer<String, String> {
    val props = Properties()
    props["bootstrap.servers"] = brokers
    props["key.serializer"] = StringSerializer::class.java.canonicalName
    props["value.serializer"] = StringSerializer::class.java.canonicalName
    return KafkaProducer<String, String>(props)
}

fun createTopics(topics: List<String>, bootstrapServers: String) {
    AdminClient.create(mapOf(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers)).use { adminClient ->
        for (topic in topics) {
            val createTopicFuture = adminClient.createTopics(listOf(NewTopic(topic, 1, 1)))
            val topicCreationResult = createTopicFuture.values().get(topic)
            topicCreationResult?.get()
        }
    }
}
