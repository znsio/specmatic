package run.qontract.core.pattern

import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.value.KafkaMessage
import run.qontract.core.value.NullValue

data class KafkaMessagePattern(val target: String = "", val key: Pattern = NoContentPattern, val content: Pattern = StringPattern) {
    fun matches(message: KafkaMessage, resolver: Resolver): Result {
        if(message.target != target)
            return Result.Failure("Expected target $target, got $message.target")

        val keyMatch = key.matches(message.key ?: NullValue, resolver)
        if(keyMatch !is Result.Success)
            return keyMatch

        return content.matches(message.content, resolver)
    }
}
