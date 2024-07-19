package io.specmatic.core

import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import io.specmatic.core.pattern.ContractException

internal class ResolverKtTest {
    @Test
    fun `it should throw an exception when the request pattern does not exist`() {
        assertThatThrownBy { Resolver().getPattern("(NonExistentPattern)") }.isInstanceOf(ContractException::class.java)
    }
}