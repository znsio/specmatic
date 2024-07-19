package io.specmatic.stub

import io.specmatic.test.HttpClient
import java.io.Closeable

interface ContractStub : Closeable {
    val client: HttpClient

    // Java helper
    fun setExpectation(json: String)
}
