package io.specmatic.test

import com.ezylang.evalex.Expression
import io.specmatic.core.Result
import io.specmatic.core.TestResult
import io.specmatic.core.filters.FilterableExpression
import io.specmatic.core.filters.ScenarioFilterTags
import io.specmatic.core.filters.ScenarioMetadata

data class TestResultRecord(
    val path: String,
    val method: String,
    val responseStatus: Int,
    val result: TestResult,
    val sourceProvider: String? = null,
    val sourceRepository: String? = null,
    val sourceRepositoryBranch: String? = null,
    val specification: String? = null,
    val serviceType: String? = null,
    val actualResponseStatus: Int = 0,
    val scenarioResult: Result? = null,
    val isValid: Boolean = true,
    val isWip: Boolean = false,
    val requestContentType: String? = null
) : FilterableExpression
{
    val isExercised = result !in setOf(TestResult.MissingInSpec, TestResult.NotCovered)
    val isCovered = result !in setOf(TestResult.MissingInSpec, TestResult.NotCovered)

    fun isConnectionRefused() = actualResponseStatus == 0

    fun toScenarioMetadata(): ScenarioMetadata {
        return ScenarioMetadata(method, path, responseStatus, emptySet(), emptySet(), "")
    }

    override fun populateExpressionData(expression: Expression): Expression {
        return expression
            .with(ScenarioFilterTags.METHOD.key, method)
            .with(ScenarioFilterTags.PATH.key, path)
            .with(ScenarioFilterTags.STATUS_CODE.key, "")
            .with(ScenarioFilterTags.HEADER.key, "")
            .with(ScenarioFilterTags.QUERY.name, "")
            .with(ScenarioFilterTags.EXAMPLE_NAME.name, "")
    }
}