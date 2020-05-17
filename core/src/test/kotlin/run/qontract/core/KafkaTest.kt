package run.qontract.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.Ignore
import org.junit.jupiter.api.Test
import run.qontract.core.value.KafkaMessage
import run.qontract.core.value.StringValue

class KafkaTest {
    @Ignore
    @Test
    fun temp() {
        val flags = mutableSetOf<String>()

        val topic = "customer"
        val message = "hello world"

        QontractKafka().use { kafka ->
            kafka.setupTopic(topic)
            kafka.send(topic, message)
            val messages = kafka.fetch(topic)

            assertThat(messages.single()).isEqualTo(KafkaMessage("topic", null, StringValue("message")))
            flags.add("handler was called")
        }

        assertThat(flags).contains("handler was called")
    }
}
