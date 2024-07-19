package io.specmatic.core.pattern

data class SimpleValue(val data: String): RowValue {
    override fun fetch(): String = data
}