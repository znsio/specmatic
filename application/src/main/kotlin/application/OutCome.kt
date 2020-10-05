package application

data class OutCome<ResultType>(val result: ResultType?, val errorMessage: String = "") {
    fun <NewResultType> onSuccess(fn: (ResultType) -> OutCome<NewResultType>): OutCome<NewResultType> {
        return when {
            result != null -> fn(result)
            else -> OutCome(null, errorMessage)
        }
    }
}