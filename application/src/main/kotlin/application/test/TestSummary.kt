package application.test

data class TestSummary(val success: Int, val aborted: Int, val failure: Int) {
    val message: String = "Tests run: ${success + aborted + failure}, Failures: $failure, Aborted: $aborted"
    val total: Int = success + aborted + failure
}
