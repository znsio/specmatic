package io.specmatic.test

data class ScenarioContext(
    val parameters: MutableMap<String, Any> = mutableMapOf(), // To store parameter names and their values
    val bodyValues: MutableMap<String, Any> = mutableMapOf() // To store body keys and their corresponding values
) {
    fun isEmpty() = parameters.isEmpty() && bodyValues.isEmpty()
}
