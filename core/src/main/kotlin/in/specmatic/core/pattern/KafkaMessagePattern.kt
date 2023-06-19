package `in`.specmatic.core.pattern

import `in`.specmatic.core.Resolver
import `in`.specmatic.core.Result
import `in`.specmatic.core.value.EmptyString
import `in`.specmatic.core.value.KafkaMessage
import `in`.specmatic.core.value.StringValue

const val KAFKA_MESSAGE_BREADCRUMB = "KAFKA-MESSAGE"

data class KafkaMessagePattern(val topic: String = "", val key: Pattern = EmptyStringPattern, val value: Pattern = StringPattern()) {
    fun matches(message: KafkaMessage, resolver: Resolver): Result = attempt(KAFKA_MESSAGE_BREADCRUMB) {
        try {
            matchTopics(message).ifSuccess {
                matchKey(message, resolver)
            }.ifSuccess {
                matchValue(message, resolver)
            }
        } catch (e: ContractException) {
            e.failure()
        }.breadCrumb(KAFKA_MESSAGE_BREADCRUMB)
    }

    private fun matchValue(message: KafkaMessage, resolver: Resolver): Result = try {
        val parsedValue = when (message.value) {
            is StringValue -> value.parse(message.value.string, resolver)
            else -> message.value
        }

        value.matches(parsedValue, resolver)
    } catch (e: ContractException) {
        e.failure()
    }.breadCrumb("VALUE")

    private fun matchKey(message: KafkaMessage, resolver: Resolver): Result = try {
        val parsedKey = when (message.key) {
            is StringValue -> key.parse(message.key.string, resolver)
            else -> message.key
        }

        key.matches(parsedKey ?: EmptyString, resolver)
    } catch (e: ContractException) {
        e.failure()
    }.breadCrumb("KEY")

    private fun matchTopics(message: KafkaMessage): Result = when (message.topic) {
        topic -> Result.Success()
        else -> Result.Failure("Expected topic $topic, got $message.topic").breadCrumb("TOPIC")
    }

    fun encompasses(other: KafkaMessagePattern, thisResolver: Resolver, otherResolver: Resolver): Result =
            attempt(KAFKA_MESSAGE_BREADCRUMB) {
                topicsShouldMatch(other).ifSuccess {
                    key.encompasses(other.key, otherResolver, thisResolver).breadCrumb("KEY")
                }.ifSuccess {
                    value.encompasses(other.value, thisResolver, otherResolver).breadCrumb("VALUE")
                }.breadCrumb(KAFKA_MESSAGE_BREADCRUMB)
            }

    private fun topicsShouldMatch(other: KafkaMessagePattern): Result = when (topic) {
        other.topic -> Result.Success()
        else -> Result.Failure("Expected topic $topic, got ${other.topic}").breadCrumb("TOPIC")
    }

    fun newBasedOn(row: Row, resolver: Resolver): List<KafkaMessagePattern> {
        val newKeys = resolver.withCyclePrevention(key) { cyclePreventedResolver ->
            key.newBasedOn(row, cyclePreventedResolver)
        }
        val newValues = resolver.withCyclePrevention(key) { cyclePreventedResolver ->
            value.newBasedOn(row, cyclePreventedResolver)
        }

        return newKeys.flatMap { newKey ->
            newValues.map { newValue ->
                KafkaMessagePattern(topic, newKey, newValue)
            }
        }
    }

    fun generate(resolver: Resolver): KafkaMessage =
            // Cycle prevention not needed because StringValue expected for both key and value
            KafkaMessage(topic, key.generate(resolver) as StringValue, value.generate(resolver) as StringValue)
}
