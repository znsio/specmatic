package io.specmatic.reports

import kotlinx.serialization.Serializable

@Serializable
data class CentralContractRepoReportJson(
    val specifications: List<SpecificationRow>
)

@Serializable
data class SpecificationRow(
    val specification: String,
    val serviceType: String?,
    val operations: List<SpecificationOperation>
)

@Serializable
data class SpecificationOperation(
    val path: String,
    val method: String,
    val responseCode: Int
)