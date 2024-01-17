package `in`.specmatic.core.pattern

import `in`.specmatic.core.DefaultExampleResolver
import `in`.specmatic.core.OMIT
import `in`.specmatic.core.References
import `in`.specmatic.core.value.JSONArrayValue
import `in`.specmatic.core.value.JSONComposite
import `in`.specmatic.core.value.JSONObjectValue

const val DEREFERENCE_PREFIX = "$"
const val FILENAME_PREFIX = "@"

data class Row(
    val columnNames: List<String> = emptyList(),
    val values: List<String> = emptyList(),
    val variables: Map<String, String> = emptyMap(),
    val references: Map<String, References> = emptyMap(),
    val name: String = "",
    val fileSource: String? = null,
    val requestBodyJSONExample: JSONExample? = null
) {
    constructor(examples: Map<String, String>) :this(examples.keys.toList(), examples.values.toList())

    private val cells = columnNames.zip(values.map { it }).toMap().toMutableMap()

    fun noteRequestBody(): Row {
        if(!this.containsField("(REQUEST-BODY)"))
            return this

        val requestBody = this.getField("(REQUEST-BODY)").trim()

        return try {
            val parsed = parsedJSON(requestBody)

            if(parsed is JSONComposite)
                this.copy(requestBodyJSONExample = JSONExample(parsed, this))
            else
                this
        } catch (e: ContractException) {
            this
        }
    }

    fun getField(columnName: String): String {
        return getValue(columnName).fetch()
    }

    private fun getValue(columnName: String): RowValue {
        val value = requestBodyJSONExample?.getValueFromTopLevelKeys(columnName) ?: cells.getValue(columnName)

        return when {
            isContextValue(value) && isReferenceValue(value) -> ReferenceValue(ValueReference(value), references)
            isContextValue(value) -> VariableValue(ValueReference(value), variables)
            isFileValue(value) -> FileValue(withoutPatternDelimiters(value).removePrefix("@"))
            else -> SimpleValue(value)
        }
    }

    private fun isFileValue(value: String): Boolean {
        return isPatternToken(value) && withoutPatternDelimiters(value).startsWith(FILENAME_PREFIX)
    }

    private fun isReferenceValue(value: String): Boolean = value.contains(".")

    private fun isContextValue(value: String): Boolean {
        return isPatternToken(value) && withoutPatternDelimiters(value).trim().startsWith(DEREFERENCE_PREFIX)
    }

    fun containsField(key: String): Boolean = requestBodyJSONExample?.hasScalarValueForKey(key) ?: cells.containsKey(key)

    fun withoutOmittedKeys(keys: Map<String, Pattern>, defaultExampleResolver: DefaultExampleResolver) = keys.filter {
        !this.containsField(withoutOptionality(it.key)) || this.getField(withoutOptionality(it.key)) !in OMIT
    }.filter {
        thisFieldHasAnExample(it.key) || defaultExampleResolver.theDefaultExampleForThisKeyIsNotOmit(it.value)
    }

    fun stepDownOneLevelInJSONHierarchy(key: String): Row {
        if(requestBodyJSONExample == null)
            return this

        if(requestBodyJSONExample.jsonObject !is JSONObjectValue)
            throw ContractException("Example provided is a JSON array, which can't contain key $key")

        val value = requestBodyJSONExample.jsonObject.findFirstChildByPath(withoutOptionality(key)) ?: return withNoJSONObjectExample()

        if(value !is JSONComposite)
            return withNoJSONObjectExample()

        return this.copy(requestBodyJSONExample = JSONExample(value, requestBodyJSONExample.originalRow))
    }

    private fun withNoJSONObjectExample() = this.copy(requestBodyJSONExample = null)

    fun dropDownIntoList(): Row {
        if(requestBodyJSONExample == null)
            return this

        if(requestBodyJSONExample.jsonObject !is JSONArrayValue)
            throw ContractException("The example provided is a JSON object, while the specification expects a list")

        val list = requestBodyJSONExample.jsonObject.list

        val firstValue = list.firstOrNull()
        if(firstValue is JSONComposite)
            return this.copy(requestBodyJSONExample = JSONExample(firstValue as JSONComposite, requestBodyJSONExample.originalRow))

        return this.copy(requestBodyJSONExample = null)
    }

    private fun thisFieldHasAnExample(key: String) =
        this.containsField(withoutOptionality(key))
}
