package `in`.specmatic.stub.report

import `in`.specmatic.conversions.convertPathParameterStyle

class StubUsageReport(
    private val configFilePath: String,
    private val allSpecApis: MutableList<StubApi> = mutableListOf(),
    private val stubLogs: MutableList<StubApi> = mutableListOf()
) {
    fun generate(): StubUsageJsonReport {
        val stubUsageJsonRows = allSpecApis.groupBy {
            StubUsageReportGroupKey(
                it.sourceProvider,
                it.sourceRepository,
                it.sourceRepositoryBranch,
                it.specification,
                it.serviceType
            )
        }.map { (key, recordsOfGroup) ->
            StubUsageJsonRow(
                type = key.sourceProvider,
                repository = key.sourceRepository,
                branch = key.sourceRepositoryBranch,
                specification = key.specification,
                serviceType = key.serviceType,
                operations = recordsOfGroup.groupBy {
                    Triple(it.path, it.method, it.responseCode)
                }.map { (operationGroup, _) ->
                    StubUsageOperation(
                        path = operationGroup.first?.let { convertPathParameterStyle(it) },
                        method = operationGroup.second,
                        responseCode = operationGroup.third,
                        count = stubLogs.count {
                            it.path == operationGroup.first
                                    && it.method == operationGroup.second
                                    && it.responseCode == operationGroup.third
                                    && it.sourceProvider == key.sourceProvider
                                    && it.sourceRepository == key.sourceRepository
                                    && it.sourceRepositoryBranch == key.sourceRepositoryBranch
                                    && it.specification == key.specification
                                    && it.serviceType == key.serviceType
                        }
                    )
                }
            )
        }
        return StubUsageJsonReport(configFilePath, stubUsageJsonRows)
    }
}

data class StubUsageReportGroupKey(
    val sourceProvider: String?,
    val sourceRepository: String?,
    val sourceRepositoryBranch: String?,
    val specification: String?,
    val serviceType: String?
)