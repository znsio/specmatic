package io.specmatic.core.pattern

import io.mockk.every
import io.mockk.mockk
import io.specmatic.core.ContractCache
import io.specmatic.core.ContractFileWithExports
import io.specmatic.core.References
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class ReferenceValueTest {
    private val contractFile = mockk<ContractFileWithExports>()
    private val contractPath = "./contract.spec"

    @BeforeEach
    fun setup() {
        every {
            contractFile.absolutePath
        }.returns(contractPath)
    }

    @Test
    fun `fetches the specified reference`() {
        val contractCache = ContractCache(mutableMapOf(contractPath to mapOf("cookie" to "abc123")))
        val referenceValue = ReferenceValue(ValueReference("auth.cookie"), mapOf("auth" to References("cookie",
            contractFile, contractCache = contractCache
        )))
        assertThat(referenceValue.fetch()).isEqualTo("abc123")
    }

    @Test
    fun `throws an exception if the reference is not found`() {
        val referenceValue = ReferenceValue(ValueReference("auth.cookie"), mapOf("auth" to References("cookie", ContractFileWithExports(""), contractCache = ContractCache())))
        assertThatThrownBy { referenceValue.fetch() }.isInstanceOf(ContractException::class.java)
    }

    @Test
    fun `throws an exception if the reference selector is not well formed`() {
        val referenceValue = ReferenceValue(ValueReference("auth.cookie"), mapOf("auth" to References("cookie", ContractFileWithExports(""), contractCache = ContractCache())))
        assertThatThrownBy { referenceValue.fetch() }.isInstanceOf(ContractException::class.java)
    }
}