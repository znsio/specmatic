package `in`.specmatic.core

class DiscardExampleDeclarations : ExampleDeclarations {
    override fun plus(more: ExampleDeclarations): ExampleDeclarations = more
    override fun plus(more: Pair<String, String>): ExampleDeclarations = this
    override fun getNewName(typeName: String, keys: Collection<String>): String =
        generateSequence(typeName) { "${it}_" }.first { it !in keys }
    override fun withComment(comment: String?): ExampleDeclarations = this
    override val comment: String = ""
    override val messages: List<String> = emptyList()
    override val examples: Map<String, String> = emptyMap()
}
