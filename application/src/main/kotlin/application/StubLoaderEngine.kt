package application

import `in`.specmatic.core.Feature
import `in`.specmatic.core.log.logger
import `in`.specmatic.mock.ScenarioStub
import `in`.specmatic.stub.loadContractStubsFromFiles
import `in`.specmatic.stub.loadContractStubsFromImplicitPaths
import org.springframework.stereotype.Component
import java.io.File

@Component
class StubLoaderEngine {
    fun loadStubs(contractPaths: List<String>, dataDirs: List<String>): List<Pair<Feature, List<ScenarioStub>>> {
        contractPaths.forEach { contractPath ->
            if(!File(contractPath).exists()) {
                logger.log("$contractPath does not exist.")
            }
        }
        return when {
            dataDirs.isNotEmpty() -> loadContractStubsFromFiles(contractPaths, dataDirs)
            else -> loadContractStubsFromImplicitPaths(contractPaths)
        }
    }
}