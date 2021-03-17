package `in`.specmatic.core

interface ExampleDeclarations {
    fun plus(more: ExampleDeclarations): ExampleDeclarations
    fun plus(more: Pair<String, String>): ExampleDeclarations
    fun getNewName(typeName: String, keys: Collection<String>): String
    fun withComment(comment: String?): ExampleDeclarations
    val comment: String?
    val messages: List<String>
    val examples: Map<String, String>
}