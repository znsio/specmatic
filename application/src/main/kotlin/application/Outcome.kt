package application

data class Outcome<ResultType>(val result: ResultType?, val errorMessage: String = "") {
    fun <NewResultType> onSuccess(fn: (ResultType) -> Outcome<NewResultType>): Outcome<NewResultType> {
        return when {
            result != null -> fn(result)
            else -> Outcome(null, errorMessage)
        }
    }

    fun handleSuccess(fn: (ResultType) -> Unit) {
        when {
            result != null -> fn(result)
            else -> Outcome(null, errorMessage)
        }
    }

    fun onFailure(fn: (String) -> Unit) {
        if(result == null)
            fn(errorMessage)
    }
}