package `in`.specmatic.core.wsdl.parser.message

import `in`.specmatic.core.value.XMLNode
import `in`.specmatic.core.value.localName
import `in`.specmatic.core.value.namespacePrefix
import `in`.specmatic.core.wsdl.parser.WSDL

class QualifiedNamespace(
    val element: XMLNode,
    val schema: XMLNode,
    private val wsdlTypeReference: String,
    val wsdl: WSDL
) :
    NamespaceQualification {

    override val namespacePrefix: List<String>
        get() {
            return if(wsdlTypeReference.namespacePrefix().isNotBlank()) {
                val fullyQualifiedName = element.fullyQualifiedNameFromQName(wsdlTypeReference)

                listOf(wsdl.mapNamespaceToPrefix(fullyQualifiedName.namespace))
            }
            else {
                val targetNamespace = schema.getAttributeValue("targetNamespace")
                listOf(wsdl.mapNamespaceToPrefix(targetNamespace))
            }
        }

    override val nodeName: String
        get() {
            val nodeName = element.getAttributeValue("name")

            val fullyQualifiedName = if(wsdlTypeReference.isNotBlank()) {
                element.fullyQualifiedNameFromQName(wsdlTypeReference)
            }
            else {
                element.fullyQualifiedName(wsdl)
            }

            val mappedPrefix = wsdl.mapNamespaceToPrefix(fullyQualifiedName.namespace)

            return "${mappedPrefix}:${nodeName.localName()}"
        }
}