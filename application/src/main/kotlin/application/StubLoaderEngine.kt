package application

import io.specmatic.core.Feature
import io.specmatic.core.getConfigFilePath
import io.specmatic.core.loadSpecmaticConfigOrDefault
import io.specmatic.core.log.logger
import io.specmatic.core.utilities.ContractPathData
import io.specmatic.mock.ScenarioStub
import io.specmatic.stub.loadContractStubsFromFiles
import io.specmatic.stub.loadContractStubsFromImplicitPaths
import org.springframework.stereotype.Component
import java.io.File

@Component
class StubLoaderEngine {
    fun loadStubs(
        contractPathDataList: List<ContractPathData>,
        dataDirs: List<String>,
        specmaticConfigPath: String? = null,
        strictMode: Boolean
    ): List<Pair<Feature, List<ScenarioStub>>> {
        contractPathDataList.forEach { contractPath ->
            if(!File(contractPath.path).exists()) {
                logger.log("$contractPath does not exist.")
            }
        }

        val specmaticConfig = loadSpecmaticConfigOrDefault(specmaticConfigPath ?: getConfigFilePath())
        val allDataDirs = dataDirs.plus(specmaticConfig.examples).distinctBy { File(it).canonicalPath }

        val explicitStubs = loadContractStubsFromFiles(contractPathDataList, allDataDirs, specmaticConfig, strictMode)
        val implicitStubs = loadContractStubsFromImplicitPaths(contractPathDataList, specmaticConfig)

        return explicitStubs.plus(implicitStubs)
    }
}