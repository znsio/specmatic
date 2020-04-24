package run.qontract.core.value

import run.qontract.core.pattern.ExactMatchPattern
import run.qontract.core.pattern.Pattern

//object EmptyString : Value {
//    override val httpContentType = "text/plain"
//
//    override fun displayableValue(): String = ""
//    override fun toStringValue() = ""
//    override fun displayableType(): String = "empty string"
//    override fun toPattern(): Pattern = ExactMatchPattern(this)
//
//    override fun equals(other: Any?): Boolean {
//        return when(other) {
//            EmptyString -> true
//            is StringValue -> return other.string == ""
//            else -> false
//        }
//    }
//
//    override fun toString() = ""
//}

val EmptyString = StringValue()
