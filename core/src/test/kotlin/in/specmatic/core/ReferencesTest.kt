package `in`.specmatic.core

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.test.HttpClient
import io.mockk.verify

internal class ReferencesTest {
    private val absoluteContractPath = "/contract.spec"

    @Test
    fun `returns cached value`() {
        val mockContractFile = mockContractFile()

        val references = References("cookie", mockContractFile, contractCache = contractCache(mapOf("cookie" to "abc123")))
        assertThat(references.lookup("cookie")).isEqualTo("abc123")
    }

    private fun contractCache(values: Map<String, String>) = ContractCache(mutableMapOf(absoluteContractPath to values))

    private fun mockContractFile(): ContractFileWithExports {
        val mockContractFile = mockk<ContractFileWithExports>()

        every {
            mockContractFile.absolutePath
        }.returns(absoluteContractPath)

        return mockContractFile
    }

    private val baseURL = "http://service"

    @Test
    fun `executes qontract as test to fetch values if the cache is empty`() {
        val results = Results(listOf(Result.Success(mapOf("name" to "Jack")), Result.Success(mapOf("address" to "Baker Street"))))

        val feature = mockFeature(results)
        val contractFileWithExports = mockContractFileWithExports(feature)
        every {
            contractFileWithExports.runContractAndExtractExports(any(), any(), any(), any())
        }.returns(mapOf("name" to "Jack", "address" to "Baker Street"))

        val references = References("person", contractFileWithExports, baseURLs = mapOf("person.$CONTRACT_EXTENSION" to baseURL), contractCache = ContractCache())

        assertThat(references.lookup("name")).isEqualTo("Jack")
        assertThat(references.lookup("address")).isEqualTo("Baker Street")
    }

    @Test
    fun `second feature looks up the contract cache and finds the values from the old feature`() {
        val results = Results(listOf(Result.Success(mapOf("name" to "Jack")), Result.Success(mapOf("address" to "Baker Street"))))

        val feature = mockFeature(results)
        val contractFileWithExports = mockContractFileWithExports(feature)
        every {
            contractFileWithExports.runContractAndExtractExports(any(), any(), any(), any())
        }.returns(mapOf("name" to "Jack", "address" to "Baker Street"))

        val contractCache = ContractCache()

        val references1 = References("person", contractFileWithExports, baseURLs = mapOf("person.$CONTRACT_EXTENSION" to baseURL), contractCache = contractCache)
        assertThat(references1.lookup("name")).isEqualTo("Jack")
        assertThat(references1.lookup("address")).isEqualTo("Baker Street")

        val references2 = References("person", contractFileWithExports, baseURLs = mapOf("person.$CONTRACT_EXTENSION" to baseURL), contractCache = contractCache)
        assertThat(references2.lookup("name")).isEqualTo("Jack")
        assertThat(references2.lookup("address")).isEqualTo("Baker Street")

        verify(exactly = 1) {
            contractFileWithExports.runContractAndExtractExports(any(), any(), any(), any())
        }
    }

    @Test
    fun `fails if the executed qontract return errors`() {
        val results = Results(listOf(Result.Success(mapOf("name" to "Jack")), Result.Failure("Failed")))

        val feature = mockFeature(results)
        val contractFileWithExports = mockContractFileWithExports(feature)
        every {
            contractFileWithExports.runContractAndExtractExports(any(), any(), any(), any())
        }.throws(ContractException("Failure"))

        val references = References("person", contractFileWithExports, baseURLs = mapOf("person.$CONTRACT_EXTENSION" to baseURL), contractCache = ContractCache())

        assertThatThrownBy { references.lookup("name") }.isInstanceOf(ContractException::class.java)
    }

    private fun mockContractFileWithExports(mockFeature: Feature): ContractFileWithExports {
        val qontractFileMock = mockk<ContractFileWithExports>()
        every {
            qontractFileMock.readFeatureForValue(any())
        }.returns(mockFeature)
        every {
            qontractFileMock.path
        }.returns("person.$CONTRACT_EXTENSION")
        every {
            qontractFileMock.absolutePath
        }.returns("/path/to/file")
        return qontractFileMock
    }

    private fun mockFeature(results: Results): Feature {
        val mockFeature = mockk<Feature>()
        every {
            mockFeature.executeTests(match { (it as HttpClient).baseURL == baseURL }, any())
        }.returns(results)
        every {
            mockFeature.copy(testVariables = any(), testBaseURLs = any())
        }.returns(mockFeature)
        return mockFeature
    }

    @Test
    fun `throws exception if specified key is not found`() {
        val contractFileName = "contract.spec"
        val references = References("cookie", ContractFileWithExports(contractFileName), contractCache = ContractCache(mutableMapOf(contractFileName to mapOf("cookie" to "abc123"))))
        assertThatThrownBy { references.lookup("non-existent") }.isInstanceOf(ContractException::class.java)
    }

    @Test
    fun `runs the reference test only once across all contracts`() {
        val contractFile = ContractFileWithExports("test.spec")
        val contractCache = ContractCache()
        val references = References("cookie", contractFile, contractCache = contractCache)
        contractCache.update(contractFile.absolutePath) {
            mapOf("key" to "value")
        }

        assertThat(references.lookup("key")).isEqualTo("value")
    }
}