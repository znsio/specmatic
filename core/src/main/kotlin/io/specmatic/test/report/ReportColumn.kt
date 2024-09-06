package io.specmatic.test.report

import kotlin.math.max

data class ReportColumn(
    val name: String,
    val columnFormat: String,
    val maxSizeOfRowInThisColumn: Int
) {
    constructor(name: String, maxSizeInThisColumn: Int) : this(
        name = name,
        columnFormat = "%-${max(maxSizeInThisColumn, name.length)}s",
        maxSizeOfRowInThisColumn = max(maxSizeInThisColumn, name.length)
    )
}