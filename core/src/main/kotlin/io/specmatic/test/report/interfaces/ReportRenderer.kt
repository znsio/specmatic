package io.specmatic.test.report.interfaces

import io.specmatic.core.SpecmaticConfig

interface ReportRenderer {
    fun render(reportInput: ReportInput, specmaticConfig: SpecmaticConfig): String
}