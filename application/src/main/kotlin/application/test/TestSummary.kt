package application.test

data class TestSummary(val success: Int, val partialSuccesses: Int, val aborted: Int, val failure: Int) {
    val message: String
        get() {
            val total = success + aborted + failure
            val partialSuccessNote = if(partialSuccesses > 0) " (of which $partialSuccesses are partial)" else ""
            return "Tests run: $total, Successes: ${success}$partialSuccessNote, Failures: $failure, Errors: $aborted"
        }
}
