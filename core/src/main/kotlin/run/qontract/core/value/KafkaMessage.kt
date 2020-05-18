package run.qontract.core.value

data class KafkaMessage(val target: String = "", val key: StringValue? = null, val value: Value = EmptyString) {
    fun toDisplayableString(): String {
        return """Topic: $target; Key: ${key?.displayableValue()}; Value: ${value.displayableValue()}"""
    }
}
