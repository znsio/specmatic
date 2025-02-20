package io.specmatic.core.pattern

import io.specmatic.core.value.StringValue
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class ISO8601 {
    companion object{
        fun validatedStringValue(time: String): StringValue {
            return try {
                LocalTime.parse(time, DateTimeFormatter.ISO_TIME)
                StringValue(time)
            } catch (e: Exception) {
                throw ContractException("Error while parsing $time as per ISO 8601: ${e.message}")
            }
        }

        val currentTime: String get() =
            LocalTime.now().format(
                DateTimeFormatter.ISO_TIME
            )
    }
}