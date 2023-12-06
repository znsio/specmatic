package `in`.specmatic.core.pattern

import java.time.format.DateTimeFormatter

private const val DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ssXXX"
private const val DATE_FORMAT = "yyyy-MM-dd"

class RFC3339 {
    companion object{
        val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern(DATETIME_FORMAT)
        val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern(DATE_FORMAT)
    }
}