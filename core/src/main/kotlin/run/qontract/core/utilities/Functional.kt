package run.qontract.core.utilities

import run.qontract.core.pattern.Pattern
import run.qontract.core.pattern.withoutOptionality
import run.qontract.core.pattern.containsKey

fun <ValueType>flatZip(map1: Map<String, Pattern>, map2: Map<String, ValueType>): List<Triple<String, Pattern, ValueType>> {
    return map1.filterKeys { key -> containsKey(map2, key) }.map { entry ->
        Triple(withoutOptionality(entry.key), entry.value, lookupValue(map2, entry.key))
    }
}

internal fun <ValueType> lookupValue(map: Map<String, ValueType>, key: String): ValueType = map.getValue(key.removeSuffix("?"))

fun flatZipNullable(expected: Map<String, Any>, actual: Map<String, Any>): List<Triple<String, Any, Any>> {
    return expected.map { entry -> Triple(entry.key, entry.value, actual[entry.key] ?: "") }
}
