package run.qontract.core.value

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.Feature
import run.qontract.core.NamedStub
import run.qontract.core.Result
import run.qontract.core.toGherkinFeature
import run.qontract.mock.ScenarioStub

internal class KafkaMessageTest {
    @Test
    fun `should convert a Kafka message with a key to qontract`() {
        val message = KafkaMessage("customers", StringValue("name"), StringValue("data"))
        val stub = NamedStub("New Kafka scenario", ScenarioStub(kafkaMessage = message))
        val featureString = toGherkinFeature(stub)

        println(featureString)

        val feature = Feature(featureString)
        assertThat(feature.matchesMockKafkaMessage(message)).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `should convert a Kafka message without a key to qontract`() {
        val message = KafkaMessage("customers", null, StringValue("data"))
        val stub = NamedStub("New Kafka scenario", ScenarioStub(kafkaMessage = message))
        val featureString = toGherkinFeature(stub)

        println(featureString)

        val feature = Feature(featureString)
        assertThat(feature.matchesMockKafkaMessage(message)).isInstanceOf(Result.Success::class.java)
    }
}