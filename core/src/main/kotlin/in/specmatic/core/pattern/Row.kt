package `in`.specmatic.core.pattern

import `in`.specmatic.core.OMIT
import `in`.specmatic.core.References
import `in`.specmatic.core.jsonObjectToValues
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.ScalarValue

const val DEREFERENCE_PREFIX = "$"
const val FILENAME_PREFIX = "@"

data class JSONObjectExample(val jsonObject: JSONObjectValue, val originalRow: Row) {
    fun containsKey(key: String): Boolean =
        jsonObject.jsonObject[key] is ScalarValue

    fun getValueFromTopLevelKeys(columnName: String): String? =
        jsonObject.jsonObject[columnName]?.toStringLiteral()
}

data class Row(
    val columnNames: List<String> = emptyList(),
    val values: List<String> = emptyList(),
    val variables: Map<String, String> = emptyMap(),
    val references: Map<String, References> = emptyMap(),
    val name: String = "",
    val fileSource: String? = null,
    val jsonObjectExample: JSONObjectExample? = null
) {
    private val cells = columnNames.zip(values.map { it }).toMap().toMutableMap()

    fun noteRequestBody(): Row {
        if(!this.containsField("(REQUEST-BODY)"))
            return this

        val requestBody = this.getField("(REQUEST-BODY)").trim()

        return try {
            this.copy(jsonObjectExample = JSONObjectExample(parsedJSONObject(requestBody), this))
        } catch (e: ContractException) {
            this
        }
    }

    fun flattenRequestBodyIntoRow(): Row {
        val jsonValue = parsedJSON(this.getField("(REQUEST-BODY)"))
        if (jsonValue !is JSONObjectValue)
            throw ContractException("Only JSON objects are supported as request body examples")

        val values: List<Pair<String, String>> = jsonObjectToValues(jsonValue)

        return Row(columnNames = values.map { it.first }, values = values.map { it.second }, name = name)
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
        val value = if(columnName in cells) cells.getValue(columnName) else jsonObjectExample?.getValueFromTopLevelKeys(columnName) ?: ""

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

    fun containsField(key: String): Boolean = jsonObjectExample?.containsKey(key) == true || cells.containsKey(key)

    fun withoutOmittedKeys(keys: Map<String, Pattern>) = keys.filter {
        !this.containsField(withoutOptionality(it.key)) || this.getField(withoutOptionality(it.key)) !in OMIT
    }

    fun dropDownTo(key: String): Row {
        if(jsonObjectExample == null)
            return this

        val value = jsonObjectExample.jsonObject.findFirstChildByPath(withoutOptionality(key)) ?: return withNoJSONObjectExample()

        if(value !is JSONObjectValue)
            return withNoJSONObjectExample()

        return this.copy(jsonObjectExample = JSONObjectExample(value, jsonObjectExample.originalRow))
    }

    private fun withNoJSONObjectExample() = this.copy(jsonObjectExample = null)
}
