package io.specmatic.test.report

data class ResultStatistics (
    val totalEndpointsCount: Int,
    val missedEndpointsCount: Int,
    val partiallyMissedEndpointsCount: Int,
    val notImplementedAPICount: Int,
    val partiallyNotImplementedAPICount: Int
)