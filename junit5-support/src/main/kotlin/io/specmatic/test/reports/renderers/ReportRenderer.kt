package io.specmatic.test.reports.renderers

import io.specmatic.core.config.SpecmaticConfig

interface ReportRenderer<T> {
    fun render(report: T, specmaticConfig: SpecmaticConfig): String
}