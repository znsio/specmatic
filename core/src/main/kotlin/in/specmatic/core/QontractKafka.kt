package `in`.specmatic.core

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.testcontainers.containers.KafkaContainer
import `in`.specmatic.core.utilities.exceptionCauseMessage
import java.io.Closeable
import java.util.*

typealias CreateProducerPredicate = (brokers: String) -> Producer<String, String>

interface KafkaInstance {
    val bootstrapServers: String
    var portBindings: List<String>

    fun close()
}

class QontractKafkaContainer(port: Int): KafkaInstance {
    private val kafkaContainer = KafkaContainer()

    init {
        kafkaContainer.portBindings = listOf("$port:9093")
        kafkaContainer.start()
    }

    override val bootstrapServers: String
        get() = kafkaContainer.bootstrapServers

    override var portBindings: List<String>
        get() = kafkaContainer.portBindings
        set(value) {
            kafkaContainer.portBindings = value
        }

    override fun close() {
        kafkaContainer.close()
    }
}

class QontractKafka(private val kafkaInstance: KafkaInstance, private val createProducerPredicate: CreateProducerPredicate = ::createProducer) : Closeable {
    constructor(kafkaPort: Int = 9093) : this(QontractKafkaContainer(kafkaPort))

    val bootstrapServers: String
        get() = kafkaInstance.bootstrapServers

    fun send(topic: String, key: String, message: String) {
        createProducerPredicate(kafkaInstance.bootstrapServers).use { producer ->
            val producerRecord = ProducerRecord(topic, key, message)
            val future = producer.send(producerRecord)
            future.get()
        }
    }

    fun send(topic: String, message: String) {
        createProducerPredicate(kafkaInstance.bootstrapServers).use { producer ->
            val producerRecord = ProducerRecord<String, String>(topic, message)
            val future = producer.send(producerRecord)
            future.get()
        }
    }

    override fun close() {
        kafkaInstance.close()
    }
}

fun createConsumer(brokers: String, commit: Boolean): Consumer<String, String> {
    val props = Properties()
    props["bootstrap.servers"] = brokers
    props["group.id"] = "specmatic"
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
            try {
                    val createTopicFuture = adminClient.createTopics(listOf(NewTopic(topic, 1, 1)))
                val topicCreationResult = createTopicFuture.values()[topic]
                topicCreationResult?.get()
            } catch(e: Throwable) {
                println("Couldn't create topic $topic: ${exceptionCauseMessage(e)}")
            }
        }
    }
}
