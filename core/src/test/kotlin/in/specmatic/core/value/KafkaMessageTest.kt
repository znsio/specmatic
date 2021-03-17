package `in`.specmatic.core.value

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import `in`.specmatic.core.parseGherkinStringToFeature
import `in`.specmatic.core.NamedStub
import `in`.specmatic.core.Result
import `in`.specmatic.core.toGherkinFeature
import `in`.specmatic.mock.ScenarioStub

internal class KafkaMessageTest {
    @Test
    fun `should convert a Kafka message with a key to qontract`() {
        val message = KafkaMessage("customers", StringValue("name"), StringValue("data"))
        val stub = NamedStub("New Kafka scenario", ScenarioStub(kafkaMessage = message))
        val featureString = toGherkinFeature(stub)

        println(featureString)

        val feature = parseGherkinStringToFeature(featureString)
        assertThat(feature.matchesMockKafkaMessage(message)).isInstanceOf(Result.Success::class.java)
        assertThat(featureString.trim()).isEqualTo("""Feature: New Feature
  Scenario: New Kafka scenario
    * kafka-message customers (string) (string)""")
    }

    @Test
    fun `should convert a Kafka message without a key to qontract`() {
        val message = KafkaMessage("customers", null, StringValue("data"))
        val stub = NamedStub("New Kafka scenario", ScenarioStub(kafkaMessage = message))
        val featureString = toGherkinFeature(stub)

        println(featureString)

        val feature = parseGherkinStringToFeature(featureString)
        assertThat(feature.matchesMockKafkaMessage(message)).isInstanceOf(Result.Success::class.java)
        assertThat(featureString.trim()).isEqualTo("""Feature: New Feature
  Scenario: New Kafka scenario
    * kafka-message customers (string)""")
    }
}
