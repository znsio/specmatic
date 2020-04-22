package run.qontract.core.versioning

import run.qontract.core.versioning.ContractIdentifier
import run.qontract.core.versioning.PointerInfo
import run.qontract.core.Results
import java.io.File

interface RepoProvider {
    fun readContract(identifier: ContractIdentifier): String
    fun updateContract(identifier: ContractIdentifier, contractFile: File)
    fun testBackwardCompatibility(identifier: ContractIdentifier, contractFile: File): Results
    fun addContract(identifier: ContractIdentifier, contractFileWithUpdate: File): PointerInfo
    fun getContractData(identifier: ContractIdentifier): String
    fun getContractFilePath(identifier: ContractIdentifier): String
}
