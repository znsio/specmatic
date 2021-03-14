package run.qontract.core

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import run.qontract.core.pattern.ContractException
import run.qontract.test.HttpClient

internal class ReferencesTest {
    @Test
    fun `returns cached value`() {
        val references = References("cookie", QontractFilePath(""), valuesCache = mapOf("cookie" to "abc123"))
        assertThat(references.lookup("cookie")).isEqualTo("abc123")
    }

    @Test
    fun `executes qontract as test to fetch values if the cache is empty`() {
        val results = Results(listOf(Result.Success(mapOf("name" to "Jack")), Result.Failure("Failed")))
        val baseURL = "http://service"

        val feature = mockFeature(baseURL, results)
        val qontractFile = mockQontractFilePath(feature)

        val references = References("person", qontractFile, baseURLs = mapOf("person.qontract" to baseURL))

        assertThatThrownBy { references.lookup("name") }.isInstanceOf(ContractException::class.java)
    }

    @Test
    fun `fails if the executed qontract return errors`() {
        val results = Results(listOf(Result.Success(mapOf("name" to "Jack")), Result.Success(mapOf("address" to "Baker Street"))))
        val baseURL = "http://service"

        val feature = mockFeature(baseURL, results)
        val qontractFile = mockQontractFilePath(feature)

        val references = References("person", qontractFile, baseURLs = mapOf("person.qontract" to baseURL))

        assertThat(references.lookup("name")).isEqualTo("Jack")
        assertThat(references.lookup("address")).isEqualTo("Baker Street")
    }

    private fun mockQontractFilePath(mockFeature: Feature): QontractFilePath {
        val qontractFileMock = mockk<QontractFilePath>()
        every {
            qontractFileMock.readFeatureForValue(any())
        }.returns(mockFeature)
        every {
            qontractFileMock.path
        }.returns("person.qontract")
        return qontractFileMock
    }

    private fun mockFeature(baseURL: String, results: Results): Feature {
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
        val references = References("cookie", QontractFilePath(""), valuesCache = mapOf("cookie" to "abc123"))
        assertThatThrownBy { references.lookup("non-existent") }.isInstanceOf(ContractException::class.java)
    }
}