package run.qontract.core.pattern

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import run.qontract.core.QontractFilePath
import run.qontract.core.References

internal class ReferenceValueTest {
    @Test
    fun `fetches the speified reference`() {
        val referenceValue = ReferenceValue(ValueReference("auth.cookie"), mapOf("auth" to References("cookie", QontractFilePath(""), valuesCache = mapOf("cookie" to "abc123"))))
        assertThat(referenceValue.fetch()).isEqualTo("abc123")
    }

    @Test
    fun `throws an exception if the reference is not found`() {
        val referenceValue = ReferenceValue(ValueReference("auth.cookie"), mapOf("auth" to References("cookie", QontractFilePath(""), valuesCache = emptyMap())))
        assertThatThrownBy { referenceValue.fetch() }.isInstanceOf(ContractException::class.java)
    }

    @Test
    fun `throws an exception if the reference selector is not well formed`() {
        val referenceValue = ReferenceValue(ValueReference("auth.cookie"), mapOf("auth" to References("cookie", QontractFilePath(""), valuesCache = emptyMap())))
        assertThatThrownBy { referenceValue.fetch() }.isInstanceOf(ContractException::class.java)
    }
}