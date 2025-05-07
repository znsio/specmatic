package io.specmatic.core

import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.Value

sealed interface DataRepresentation {
    fun insert(segments: List<String>, value: Value): DataRepresentation
    fun toValue(): Value

    data class NodeData(val data: Map<String, DataRepresentation> = emptyMap()): DataRepresentation {

        override fun insert(segments: List<String>, value: Value): NodeData {
            require(segments.isNotEmpty()) { "Cannot insert into MapData with empty path" }
            val currentSegment = segments.first()
            val remaining = segments.drop(1)

            if (remaining.isEmpty()) {
                return copy(data = data + (currentSegment to from(value)))
            }

            val child = data[currentSegment] ?: NodeData()
            val updatedChild = child.insert(remaining, value)
            return copy(data = data + (currentSegment to updatedChild))
        }

        override fun toValue(): JSONObjectValue {
            return JSONObjectValue(data.mapValues { it.value.toValue() })
        }
    }

    class LeafData(val data: Value) : DataRepresentation {
        override fun insert(segments: List<String>, value: Value): LeafData {
            throw UnsupportedOperationException("LeafData does not support inserts")
        }

        override fun toValue(): Value {
            return data
        }
    }

    fun insert(key: String, value: Value): DataRepresentation {
        val segments = key.toSegments()
        return insert(segments, value)
    }

    fun String.toSegments(): List<String> {
        return this.split(".").filterNot(String::isEmpty)
    }

    companion object {
        fun from(value: Value): DataRepresentation {
            return when (value) {
                is JSONObjectValue -> from(value.jsonObject)
                else -> LeafData(value)
            }
        }

        fun from(map: Map<String, Value>): DataRepresentation {
            return map.entries.fold(NodeData() as DataRepresentation) { acc, data ->
                acc.insert(data.key, data.value)
            }
        }
    }
}
