package run.qontract.core

import run.qontract.core.pattern.ContractException
import run.qontract.core.utilities.contractFilePath
import run.qontract.core.utilities.readFile
import run.qontract.fake.ContractFake
import run.qontract.test.HttpClient

data class Contract(val contractGherkin: String, val majorVersion: Int = 0, val minorVersion: Int = 0) {
    fun startFake(port: Int) = ContractFake(contractGherkin, emptyList(), "localhost", port)

    fun test(endPoint: String) {
        val contractBehaviour = ContractBehaviour(contractGherkin)
        val results = contractBehaviour.executeTests(HttpClient(endPoint))
        if (results.hasFailures())
            throw ContractException(results.report())
    }

    fun test(fake: ContractFake) {
        test(fake.endPoint)
    }

    companion object {
        @JvmStatic
        fun fromGherkin(contractGherkin: String, majorVersion: Int, minorVersion: Int): Contract {
            return Contract(contractGherkin, majorVersion, minorVersion)
        }

        @JvmStatic
        fun behaviourFromFile(contractFilePath: String) = ContractBehaviour(readFile(contractFilePath))

        @JvmStatic
        fun behaviourFromServiceContractFile() = behaviourFromFile(contractFilePath)
    }
}