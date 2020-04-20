package application

import run.qontract.core.Results
import run.qontract.core.value.Value
import java.io.File

interface RepoProvider {
    fun readContract(identifier: ContractIdentifier): String
    fun updateContract(identifier: ContractIdentifier, contractFile: File)
    fun testBackwardCompatibility(identifier: ContractIdentifier, contractFile: File): Results
    fun addContract(identifier: ContractIdentifier, contractFileWithUpdate: File): PointerInfo
    fun getContractData(identifier: ContractIdentifier): String
}
