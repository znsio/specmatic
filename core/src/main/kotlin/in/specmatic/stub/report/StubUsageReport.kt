package `in`.specmatic.stub.report

class StubUsageReport(
    private var configFilePath: String = "",
    private val stubApis:MutableList<StubApi> = mutableListOf(),
    val logs: MutableList<StubApi> = mutableListOf()
) {
    fun addStubRequestLog(log: StubApi) {
        logs.add(log)
    }

    fun addStubApi(stubApi: StubApi) {
        stubApis.add(stubApi)
    }

    fun generate(): StubUsageJsonReport {
        val stubUsageJsonRows = stubApis.groupBy {
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
                        path = operationGroup.first,
                        method = operationGroup.second,
                        responseCode = operationGroup.third,
                        count = logs.count {
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