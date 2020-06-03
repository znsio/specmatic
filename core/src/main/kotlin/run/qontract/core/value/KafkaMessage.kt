package run.qontract.core.value

import run.qontract.core.GherkinClause
import run.qontract.core.GherkinSection.*

data class KafkaMessage(val topic: String = "", val key: StringValue? = null, val value: Value = EmptyString) {
    fun toDisplayableString(): String {
        return """Topic: $topic; Key: ${key?.displayableValue()}; Value: ${value.displayableValue()}"""
    }
}

fun toGherkinClauses(kafkaMessage: KafkaMessage): List<GherkinClause> {
    val keyTypeDeclaration = kafkaMessage.key?.typeDeclaration("KeyType")
    val valueTypeDeclaration = kafkaMessage.value.typeDeclaration("ValueType")

    val newTypes = valueTypeDeclaration.first.types.plus(keyTypeDeclaration?.first?.types ?: emptyMap())
    val gherkinTypeDeclarations = run.qontract.core.toGherkinClauses(newTypes)

    val gherkinContent = listOfNotNull("kafka-message ${kafkaMessage.topic}", keyTypeDeclaration?.first?.typeValue, valueTypeDeclaration.first.typeValue).joinToString(" ")
    val gherkinSection = if(gherkinTypeDeclarations.isEmpty()) `*` else Then
    val messageClause = GherkinClause(gherkinContent, gherkinSection)
    return listOf(messageClause).plus(gherkinTypeDeclarations)
}
