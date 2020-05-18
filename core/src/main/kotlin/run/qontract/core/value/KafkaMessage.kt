package run.qontract.core.value

data class KafkaMessage(val topic: String = "", val key: StringValue? = null, val value: Value = EmptyString) {
    fun toDisplayableString(): String {
        return """Topic: $topic; Key: ${key?.displayableValue()}; Value: ${value.displayableValue()}"""
    }
}
