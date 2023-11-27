package `in`.specmatic.core.pattern

import `in`.specmatic.core.OMIT
import `in`.specmatic.core.References
import `in`.specmatic.core.value.JSONArrayValue
import `in`.specmatic.core.value.JSONComposite
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.ScalarValue

const val DEREFERENCE_PREFIX = "$"
const val FILENAME_PREFIX = "@"

data class JSONObjectExample(val jsonObject: JSONComposite, val originalRow: Row) {
    fun containsKey(key: String): Boolean {
        return jsonObject.let {
            when(it) {
                is JSONObjectValue -> it.jsonObject[key] is ScalarValue
                is JSONArrayValue -> false
            }
        }
    }

    fun getValueFromTopLevelKeys(columnName: String): String {
        if(jsonObject !is JSONObjectValue)
            throw ContractException("The example provided is a JSON array, while the specification expects a JSON object with key $columnName")

        return jsonObject.jsonObject.getValue(columnName).toStringLiteral()
    }
}

data class Row(
    val columnNames: List<String> = emptyList(),
    val values: List<String> = emptyList(),
    val variables: Map<String, String> = emptyMap(),
    val references: Map<String, References> = emptyMap(),
    val name: String = "",
    val fileSource: String? = null,
    val jsonExample: JSONObjectExample? = null
) {
    private val cells = columnNames.zip(values.map { it }).toMap().toMutableMap()

    fun noteRequestBody(): Row {
        if(!this.containsField("(REQUEST-BODY)"))
            return this

        val requestBody = this.getField("(REQUEST-BODY)").trim()

        return try {
            this.copy(jsonExample = JSONObjectExample(parsedJSON(requestBody) as JSONComposite, this))
        } catch (e: ContractException) {
            this
        }
    }

    fun stringForOpenAPIError(): String {
        return columnNames.zip(values).joinToString(", ") { (key, value) ->
            "$key=$value"
        }
    }

    fun getField(columnName: String): String {
        return getValue(columnName).fetch()
    }

    private fun getValue(columnName: String): RowValue {
        val value = jsonExample?.getValueFromTopLevelKeys(columnName) ?: cells.getValue(columnName)

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

    fun containsField(key: String): Boolean = jsonExample?.containsKey(key) ?: cells.containsKey(key)

    fun withoutOmittedKeys(keys: Map<String, Pattern>) = keys.filter {
        !this.containsField(withoutOptionality(it.key)) || this.getField(withoutOptionality(it.key)) !in OMIT
    }

    fun dropDownIntoJSONObject(key: String): Row {
        if(jsonExample == null)
            return this

        if(jsonExample.jsonObject !is JSONObjectValue)
            throw ContractException("Example provided is a JSON array, which can't contain key $key")

        val value = jsonExample.jsonObject.findFirstChildByPath(withoutOptionality(key)) ?: return withNoJSONObjectExample()

        if(value !is JSONComposite)
            return withNoJSONObjectExample()

        return this.copy(jsonExample = JSONObjectExample(value, jsonExample.originalRow))
    }

    private fun withNoJSONObjectExample() = this.copy(jsonExample = null)

    fun dropDownIntoList(): Row {
        if(jsonExample == null)
            return this

        if(jsonExample.jsonObject !is JSONArrayValue)
            throw ContractException("The example provided is a JSON object, while the specification expects a list")

        val list = jsonExample.jsonObject.list

        val firstValue = list.firstOrNull()
        if(firstValue is JSONComposite)
            return this.copy(jsonExample = JSONObjectExample(firstValue as JSONComposite, jsonExample.originalRow))

        return this.copy(jsonExample = null)
    }
}
