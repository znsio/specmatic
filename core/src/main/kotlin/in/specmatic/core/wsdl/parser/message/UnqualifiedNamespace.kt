package `in`.specmatic.core.wsdl.parser.message

class UnqualifiedNamespace(val name: String) : NamespaceQualification {
    override val namespacePrefix: List<String>
        get() {
            return emptyList()
        }

    override val nodeName: String
        get() {
            return name
        }
}