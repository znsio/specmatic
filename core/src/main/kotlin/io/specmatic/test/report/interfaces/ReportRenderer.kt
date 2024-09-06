package io.specmatic.test.report.interfaces

import io.specmatic.core.SpecmaticConfig

interface ReportRenderer<T> {
    fun render(reportInput: T, specmaticConfig: SpecmaticConfig): String
}