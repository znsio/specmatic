package `in`.specmatic.stub

import `in`.specmatic.test.HttpClient
import java.io.Closeable

interface ContractStub : Closeable {
    val client: HttpClient

    // Java helper
    fun setExpectation(json: String)
}
