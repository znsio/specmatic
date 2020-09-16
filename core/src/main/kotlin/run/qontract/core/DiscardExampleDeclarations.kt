package run.qontract.core

class DiscardExampleDeclarations : ExampleDeclarations {
    override fun plus(more: ExampleDeclarations): ExampleDeclarations = more
    override fun plus(more: Pair<String, String>): ExampleDeclarations = this
    override fun getNewName(typeName: String, keys: Collection<String>): String = typeName
    override val messages: List<String> = emptyList()
    override val examples: Map<String, String> = emptyMap()
}
