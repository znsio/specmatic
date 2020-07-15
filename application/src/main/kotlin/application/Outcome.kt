package application

sealed class Outcome {
    abstract fun onFailure(fn: (Failure) -> Outcome?): Outcome?
}

class Failure(val message: String): Outcome() {
    override fun onFailure(fn: (Failure) -> Outcome?): Outcome? = fn(this)
}

class Success(): Outcome() {
    override fun onFailure(fn: (Failure) -> Outcome?): Outcome? = null
}
