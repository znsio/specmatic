package `in`.specmatic.core.pattern

import `in`.specmatic.core.value.JSONComposite
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.ScalarValue

data class JSONExample(val jsonObject: JSONComposite, val originalRow: Row) {
    fun hasScalarValueForKey(key: String): Boolean {
        return jsonObject.let {
            it is JSONObjectValue && it.jsonObject[key] is ScalarValue
        }
    }

    fun getValueFromTopLevelKeys(columnName: String): String {
        if(jsonObject !is JSONObjectValue)
            throw ContractException("The example provided is a JSON array, while the specification expects a JSON object with key $columnName")

        return jsonObject.jsonObject.getValue(columnName).toStringLiteral()
    }
}