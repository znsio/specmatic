package `in`.specmatic.stub

class StubUsageReport(val logs: MutableList<StubRequestLog> = mutableListOf()) {
    fun addStubRequestLog(stubRequestLog: StubRequestLog) {
        logs.add(stubRequestLog)
    }
}