package io.specmatic.core.examples.server

import io.specmatic.core.Feature
import io.specmatic.core.Result
import java.io.File

typealias SchemaExampleToResult = Pair<SchemaExample, Result?>

class SchemaExamplesView {
    companion object {
        fun schemaExamplesToTableRows(feature: Feature, schemaExamples: List<SchemaExampleToResult>): List<TableRow> {
            return schemaExamples.groupByPattern().flatMap { (pattern, patternGroup) ->
                val withMissingDiscriminators = patternGroup.withMissingDiscriminators(feature, pattern)
                withMissingDiscriminators.mapIndexed { index, it ->
                    it.toTableRow(showPath = index == 0, pathSpan = withMissingDiscriminators.size)
                }
            }
        }

        private fun SchemaExampleForView.toTableRow(showPath: Boolean, pathSpan: Int): TableRow {
            return TableRow(
                rawPath = this.groupPatternName,
                path = this.groupPatternName,
                method = this.patternName.orEmpty(),
                responseStatus = "",
                contentType = "",
                pathSpan = pathSpan,
                methodSpan = 1,
                statusSpan = 1,
                showPath = showPath,
                showMethod = pathSpan > 1,
                showStatus = false,
                example = this.exampleFile?.canonicalPath,
                exampleName = this.exampleFile?.nameWithoutExtension,
                exampleMismatchReason = this.result?.reportString()?.takeIf { it.isNotBlank() },
                isPartialFailure = this.result?.isPartialFailure() ?: false,
                isDiscriminatorBased = false,
                isSchemaBased = true,
                pathColSpan = if (pathSpan > 1) 3 else 5,
                methodColSpan = if (pathSpan > 1) 2 else 1,
                failureDetails = if (this.result is Result.Failure) this.result.toMatchFailureDetailList() else emptyList(),
            )
        }

        private fun List<SchemaExampleToResult>.groupByPattern(): Map<String, List<SchemaExampleToResult>> {
            return this.groupBy { (example, _) -> example.discriminatorBasedOn ?: example.schemaBasedOn }
        }

        private fun List<SchemaExampleToResult>.withMissingDiscriminators(feature: Feature, pattern: String): List<SchemaExampleForView> {
            val existingDiscriminators = this.map { (example, _) -> example.schemaBasedOn }.toSet()
            val discriminatorValues = feature.getAllDiscriminatorValuesIfExists(pattern)

            if (discriminatorValues.isEmpty()) {
                return this.toSchemaView(pattern) {
                    example -> example.schemaBasedOn.takeIf { this.size > 1 }
                }
            }

            val missingDiscriminators = discriminatorValues.minus(existingDiscriminators)
            return this.filterNot { (example, result) -> result == null && example.schemaBasedOn !in discriminatorValues }
                .toSchemaView(pattern) { example -> example.schemaBasedOn.takeIf { it in discriminatorValues } }
                .plus(missingDiscriminators.toMissingSchemas(pattern))
                .sortedBy { it.patternName }
        }

        private fun Set<String>.toMissingSchemas(pattern: String): List<SchemaExampleForView> {
            return this.map {
                SchemaExampleForView(
                    groupPatternName = pattern,
                    patternName = it,
                    result = null,
                    exampleFile = null
                )
            }
        }

        private fun List<SchemaExampleToResult>.toSchemaView(pattern: String, patterName: (example: SchemaExample) -> String?): List<SchemaExampleForView> {
            return this.map { (example, result) ->
                SchemaExampleForView(
                    groupPatternName = pattern,
                    patternName = patterName(example),
                    result = result,
                    exampleFile = example.file.takeIf { result != null }
                )
            }
        }
    }
}

data class SchemaExampleForView (
    val groupPatternName: String,
    val patternName: String?,
    val result: Result?,
    val exampleFile: File?
)