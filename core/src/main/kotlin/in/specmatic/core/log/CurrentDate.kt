package `in`.specmatic.core.log

import java.util.*

class CurrentDate(private val date: Calendar = Calendar.getInstance()) {
    fun getDateStringValue(): String {
        val year = date.get(Calendar.YEAR)
        val month = date.get(Calendar.MONTH)
        val day = date.get(Calendar.DATE)
        val hour = date.get(Calendar.HOUR)
        val minute = date.get(Calendar.MINUTE)
        val second = date.get(Calendar.SECOND)
        val millisecond = date.get(Calendar.MILLISECOND)

        return "$year-$month-$day $hour:$minute:$second.$millisecond"
    }
}