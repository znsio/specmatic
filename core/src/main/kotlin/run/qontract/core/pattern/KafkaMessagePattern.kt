package run.qontract.core.pattern

import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.breadCrumb
import run.qontract.core.value.EmptyString
import run.qontract.core.value.KafkaMessage
import run.qontract.core.value.NullValue
import run.qontract.core.value.StringValue

data class KafkaMessagePattern(val topic: String = "", val key: Pattern = EmptyStringPattern, val value: Pattern = StringPattern) {
    fun matches(message: KafkaMessage, resolver: Resolver): Result {
        return attempt("KAFKA-MESSAGE") { _matches(message, resolver).breadCrumb("KAFKA-MESSAGE") }
    }

    fun _matches(message: KafkaMessage, resolver: Resolver): Result {
        if(message.topic != topic)
            return Result.Failure("Expected topic $topic, got $message.topic").breadCrumb("TOPIC")

        try {
            val parsedKey = when (message.key) {
                is StringValue -> try { key.parse(message.key.string, resolver) } catch(e: ContractException) { return e.failure().breadCrumb("KEY") }
                else -> message.key
            }

            val keyMatch = key.matches(parsedKey ?: EmptyString, resolver)
            if (keyMatch !is Result.Success)
                return keyMatch.breadCrumb("KEY")

            val parsedValue = when (message.value) {
                is StringValue -> try { value.parse(message.value.string, resolver) } catch(e: ContractException) { return e.failure().breadCrumb("VALUE")}
                else -> message.value
            }

            return value.matches(parsedValue, resolver).breadCrumb("VALUE")
        } catch(e: ContractException) {
            return e.failure()
        }
    }

    fun encompasses(other: KafkaMessagePattern, thisResolver: Resolver, otherResolver: Resolver): Result {
        return attempt("KAFKA-MESSAGE") { _encompasses(other, thisResolver, otherResolver).breadCrumb("KAFKA-MESSAGE") }
    }

    fun _encompasses(other: KafkaMessagePattern, thisResolver: Resolver, otherResolver: Resolver): Result {
        if(topic != other.topic)
            return Result.Failure("Expected topic $topic, got ${other.topic}").breadCrumb("TOPIC")

        val keyResult = key.encompasses(other.key, otherResolver, thisResolver)
        if(keyResult is Result.Failure)
            return keyResult.breadCrumb("KEY")

        return value.encompasses(other.value, thisResolver, otherResolver).breadCrumb("VALUE")
    }

    fun newBasedOn(row: Row, resolver: Resolver): List<KafkaMessagePattern> {
        val newKeys = key.newBasedOn(row, resolver)
        val newValues = value.newBasedOn(row, resolver)

        return newKeys.flatMap { newKey ->
            newValues.map { newValue ->
                KafkaMessagePattern(topic, newKey, newValue)
            }
        }
    }

    fun generate(resolver: Resolver): KafkaMessage =
            KafkaMessage(topic, key.generate(resolver) as StringValue, value.generate(resolver) as StringValue)
}
