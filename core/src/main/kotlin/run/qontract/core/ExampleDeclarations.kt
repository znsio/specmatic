package run.qontract.core

interface ExampleDeclarations {
    fun plus(more: ExampleDeclarations): ExampleDeclarations
    fun plus(more: Pair<String, String>): ExampleDeclarations
    fun getNewName(typeName: String, keys: Collection<String>): String
    val messages: List<String>
    val examples: Map<String, String>
}