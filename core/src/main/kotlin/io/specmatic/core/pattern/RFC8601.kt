package io.specmatic.core.pattern

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

private val RFC8601_PATTERN: Pattern = Pattern.compile (
    """^(?<hour>2[0-3]|[01][0-9]):?(?<minute>[0-5][0-9]):?(?<second>[0-5][0-9])(?<timezone>Z|[+-](?:2[0-3]|[01][0-9])(?::?(?:[0-5][0-9]))?)?$"""
)

class RFC8601 {
    companion object{
        private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

        fun parse(dateTime: String) {
            val matcher = RFC8601_PATTERN.matcher(dateTime)
            if(!matcher.matches()) {
                throw ContractException("Error while parsing the time as per RFC 8601: $dateTime")
            }
        }

        fun currentTime(): String = LocalTime.now().format(
            timeFormatter
        )
    }
}