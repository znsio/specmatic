package io.specmatic.core.utilities

import io.specmatic.core.pattern.containsKey
import io.specmatic.core.pattern.withoutOptionality

fun <Type1, Type2>mapZip(map1: Map<String, Type1>, map2: Map<String, Type2>): List<Triple<String, Type1, Type2>> {
    return map1.filterKeys { key -> containsKey(map2, key) }.map { entry ->
        Triple(withoutOptionality(entry.key), entry.value, lookupValue(map2, entry.key))
    }
}

internal fun <ValueType> lookupValue(map: Map<String, ValueType>, key: String): ValueType = map.getValue(withoutOptionality(key))

