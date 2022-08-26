package application.test

data class TestSummary(val success: Int, val taintedSuccess: Int, val aborted: Int, val failure: Int) {
    val message: String
        get() {
            val total = success + aborted + failure
            val taintedNote = if(taintedSuccess > 0) " (of which $taintedSuccess are tainted)" else ""
            return "Tests run: $total, Successes: ${success}$taintedNote, Failures: $failure, Aborted: $aborted"
        }
}
