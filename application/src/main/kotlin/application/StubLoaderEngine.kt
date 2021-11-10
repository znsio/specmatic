package application

import `in`.specmatic.core.Feature
import `in`.specmatic.mock.ScenarioStub
import `in`.specmatic.stub.loadContractStubsFromFiles
import `in`.specmatic.stub.loadContractStubsFromImplicitPaths
import org.springframework.stereotype.Component

@Component
class StubLoaderEngine {
    fun loadStubs(contractPaths: List<String>, dataDirs: List<String>): List<Pair<Feature, List<ScenarioStub>>> {
        return when {
            dataDirs.isNotEmpty() -> loadContractStubsFromFiles(contractPaths, dataDirs)
            else -> loadContractStubsFromImplicitPaths(contractPaths)
        }
    }
}