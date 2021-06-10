package `in`.specmatic.core.pattern

import `in`.specmatic.core.ContractCache
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import `in`.specmatic.core.ContractFileWithExports
import `in`.specmatic.core.References

internal class ReferenceValueTest {
    @Test
    fun `fetches the speified reference`() {
        val referenceValue = ReferenceValue(ValueReference("auth.cookie"), mapOf("auth" to References("cookie", ContractFileWithExports(""), valuesCache = mapOf("cookie" to "abc123"), contractCache = ContractCache())))
        assertThat(referenceValue.fetch()).isEqualTo("abc123")
    }

    @Test
    fun `throws an exception if the reference is not found`() {
        val referenceValue = ReferenceValue(ValueReference("auth.cookie"), mapOf("auth" to References("cookie", ContractFileWithExports(""), valuesCache = emptyMap(), contractCache = ContractCache())))
        assertThatThrownBy { referenceValue.fetch() }.isInstanceOf(ContractException::class.java)
    }

    @Test
    fun `throws an exception if the reference selector is not well formed`() {
        val referenceValue = ReferenceValue(ValueReference("auth.cookie"), mapOf("auth" to References("cookie", ContractFileWithExports(""), valuesCache = emptyMap(), contractCache = ContractCache())))
        assertThatThrownBy { referenceValue.fetch() }.isInstanceOf(ContractException::class.java)
    }
}