package run.qontract.core.pattern

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.value.KafkaMessage
import run.qontract.core.value.NumberValue
import run.qontract.core.value.StringValue

internal class KafkaMessagePatternTest {
    @Test
    fun `should match a kafka message`() {
        val kafkaMessagePattern = KafkaMessagePattern("target", StringPattern, NumberPattern)
        kafkaMessagePattern.matches(KafkaMessage("target", StringValue("test"), NumberValue(10)), Resolver())
    }

    @Test
    fun `should not match a message with a params that don't match`() {
        val kafkaMessagePattern = KafkaMessagePattern("target", StringPattern, NumberPattern)
        val wrongTarget = KafkaMessage("different target", StringValue("test"), NumberValue(10))
        val wrongValueType = KafkaMessage("different target", StringValue("test"), StringValue("wrong value type"))

        assertThat(kafkaMessagePattern.matches(wrongTarget, Resolver())).isInstanceOf(Result.Failure::class.java)
        assertThat(kafkaMessagePattern.matches(wrongValueType, Resolver())).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `should generate multiple message types when optionals are in the payload`() {
        val patterns = KafkaMessagePattern("target", StringPattern, AnyPattern(listOf(NumberPattern, NullPattern))).newBasedOn(Row(), Resolver())
        assertThat(patterns).hasSize(2)
        assertThat(patterns[0].value).isEqualTo(NumberPattern)
        assertThat(patterns[1].value).isEqualTo(NullPattern)
    }

    @Test
    fun `should encompass itself`() {
        val pattern = KafkaMessagePattern("target", StringPattern, AnyPattern(listOf(NumberPattern, NullPattern)))
        assertThat(pattern.encompasses(pattern, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `should encompass a pattern with a smaller message`() {
        val bigger = KafkaMessagePattern("target", StringPattern, AnyPattern(listOf(NumberPattern, NullPattern)))
        val smallerNull = KafkaMessagePattern("target", StringPattern, AnyPattern(listOf(NullPattern)))
        val smallerNumber = KafkaMessagePattern("target", StringPattern, AnyPattern(listOf(NumberPattern)))

        assertThat(bigger.encompasses(smallerNull, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
        assertThat(bigger.encompasses(smallerNumber, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `should validate and pick up a matching value from examples`() {
        val pattern = KafkaMessagePattern("target", StringPattern, parsedPattern("""{"name": "(string)"}"""))
        val example = Row(listOf("name"), listOf("John Doe"))

        val newPatterns = pattern.newBasedOn(example, Resolver())

        assertThat(newPatterns).hasSize(1)

        val jsonObjectPattern = newPatterns.single().value as JSONObjectPattern
        val exactValuePattern = jsonObjectPattern.pattern.getValue("name") as ExactValuePattern

        assertThat(exactValuePattern.pattern).isEqualTo(StringValue("John Doe"))
    }

    @Test
    fun `should validate and pick up a value from examples using the from keyword`() {
        val pattern = KafkaMessagePattern("target", StringPattern, LookupRowPattern(StringPattern, "name"))
        val example = Row(listOf("name"), listOf("John Doe"))

        val newPatterns = pattern.newBasedOn(example, Resolver())
        assertThat(newPatterns).hasSize(1)

        val exactValuePattern = newPatterns.single().value as ExactValuePattern
        assertThat(exactValuePattern.pattern).isEqualTo(StringValue("John Doe"))
    }
}
