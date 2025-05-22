package io.specmatic.stub

import io.specmatic.test.LegacyHttpClient
import java.io.Closeable

interface ContractStub : Closeable {
    val client: LegacyHttpClient

    // Java helper
    fun setExpectation(json: String)
}
