package io.specmatic.core.config.v1

data class APICoverageConfiguration(
    val successCriteria: SuccessCriteria = SuccessCriteria(),
    val excludedEndpoints: List<String> = emptyList()
)

data class SuccessCriteria(
    val minThresholdPercentage: Int = 0,
    val maxMissedEndpointsInSpec: Int = 0,
    val enforce: Boolean = false
)