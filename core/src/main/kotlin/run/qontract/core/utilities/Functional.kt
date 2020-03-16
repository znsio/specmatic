package run.qontract.core.utilities

import run.qontract.core.pattern.cleanupKey
import run.qontract.core.pattern.containsKey
import run.qontract.core.pattern.lookupValue

fun flatZip(map1: Map<String, Any?>, map2: Map<String, Any?>): List<Triple<String, Any?, Any?>> {
    return map1.filterKeys { key -> containsKey(map2, key) }.map { entry ->
        Triple(cleanupKey(entry.key), entry.value, lookupValue(map2, entry.key))
    }
}


fun flatZipNullable(expected: Map<String, Any>, actual: Map<String, Any>): List<Triple<String, Any, Any>> {
    return expected.map { entry -> Triple(entry.key, entry.value, actual[entry.key] ?: "") }
}