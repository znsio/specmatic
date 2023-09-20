package application

import `in`.specmatic.core.Feature
import `in`.specmatic.core.log.logger
import `in`.specmatic.core.utilities.ContractPathData
import `in`.specmatic.mock.ScenarioStub
import `in`.specmatic.stub.loadContractStubsFromFiles
import `in`.specmatic.stub.loadContractStubsFromImplicitPaths
import org.springframework.stereotype.Component
import java.io.File

@Component
class StubLoaderEngine {
    fun loadStubs(contractPathDataList: List<ContractPathData>, dataDirs: List<String>): List<Pair<Feature, List<ScenarioStub>>> {
        contractPathDataList.forEach { contractPath ->
            if(!File(contractPath.path).exists()) {
                logger.log("$contractPath does not exist.")
            }
        }
        return when {
            dataDirs.isNotEmpty() -> loadContractStubsFromFiles(contractPathDataList, dataDirs)
            else -> loadContractStubsFromImplicitPaths(contractPathDataList)
        }
    }
}