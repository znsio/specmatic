package run.qontract.core.wsdl.parser

import run.qontract.core.pattern.ContractException
import run.qontract.core.value.XMLNode
import run.qontract.core.value.namespacePrefix
import run.qontract.core.value.withoutNamespacePrefix
import run.qontract.core.wsdl.parser.message.ComplexTypeElement
import run.qontract.core.wsdl.parser.message.WSDLPayloadElement
import run.qontract.core.wsdl.parser.message.SimpleElement

private fun getXmlnsDefinitions(wsdlNode: XMLNode): Map<String, String> {
    return wsdlNode.attributes.filterKeys {
        it.startsWith("xmlns:")
    }.mapValues {
        it.value.toStringValue()
    }.map {
        Pair(it.value, it.key.removePrefix("xmlns:"))
    }.toMap()
}

data class WSDL(private val wsdlNode: XMLNode, private val typesNode: XMLNode, val namespaceToPrefix: Map<String, String>) {
    constructor(wsdlNode: XMLNode) : this (wsdlNode, wsdlNode.getXMLNodeByName("types"), getXmlnsDefinitions(wsdlNode))

    val operations: List<XMLNode>
        get() {
        return getBinding().findChildrenByName("operation")
    }

    fun convertToGherkin(): String {
        val port = wsdlNode.getXMLNodeOrNull("service.port")
        val endpoint = wsdlNode.getXMLNodeOrNull("service.endpoint")

        val (url, soapParser) = when {
            port != null -> Pair(port.getAttributeValueAtPath("address", "location"), SOAP11Parser(this))
            endpoint != null -> Pair(endpoint.getAttributeValueAtPath("address", "location"), SOAP20Parser())
            else -> throw ContractException("Could not find the service endpoint")
        }

        return soapParser.convertToGherkin(url)
    }

    fun findComplexType(
        element: XMLNode,
        attributeName: String
    ): XMLNode {
        val fullTypeName = element.attributes.getValue(attributeName).toStringValue()
        val schema = findSchema(namespace(fullTypeName, element))
        return schema.findByNodeNameAndAttribute("complexType", "name", fullTypeName.withoutNamespacePrefix())
    }

    fun findType(
        element: XMLNode,
        attributeName: String
    ): XMLNode {
        val fullTypeName = element.attributes.getValue(attributeName).toStringValue()
        return findElement(fullTypeName.withoutNamespacePrefix(), namespace(fullTypeName, element))
    }

    private fun namespace(fullTypeName: String, element: XMLNode): String {
        val namespacePrefix = fullTypeName.namespacePrefix()
        return if (namespacePrefix.isBlank())
            ""
        else
            element.namespaces[namespacePrefix]
                ?: throw ContractException("Could not find namespace with prefix $namespacePrefix in xml node $element")
    }

    fun findElement(fullTypeReference: String): XMLNode {
        val typeName = fullTypeReference.withoutNamespacePrefix()
        val namespacePrefix = fullTypeReference.namespacePrefix()
        val namespace = wsdlNode.attributes["xmlns:$namespacePrefix"]?.toStringValue()
            ?: throw ContractException("Couldn't find the namespace for type reference $fullTypeReference")

        val schema = findSchema(namespace)

        return schema.getXMLNodeByAttributeValue("name", typeName)
    }

    fun findElement(typeName: String, namespace: String): XMLNode {
        val schema = findSchema(namespace)

        return schema.getXMLNodeByAttributeValue("name", typeName)
    }

    fun getSOAPElement(wsdlTypeReference: String): WSDLPayloadElement {
        val typeName = wsdlTypeReference.withoutNamespacePrefix() // TODO might need to do this in a cleaner way
        val namespace = this.resolveNamespace(wsdlTypeReference)

        val schema = findSchema(namespace)

        val node = schema.getXMLNodeByAttributeValue("name", typeName)

        return if(hasSimpleTypeAttribute(node)) {
            SimpleElement(wsdlTypeReference, node, this)
        } else {
            ComplexTypeElement(wsdlTypeReference, node, this)
        }
    }

    fun findSchema(namespace: String): XMLNode {
        return when {
            namespace.isBlank() -> typesNode.childNodes.filterIsInstance<XMLNode>().first()
            else -> {
                typesNode.childNodes.filterIsInstance<XMLNode>().find {
                    it.attributes["targetNamespace"]?.toStringValue() == namespace
                } ?: throw ContractException("Couldn't find schema with targetNamespace $namespace")
            }
        }
    }

    fun getComplexTypeNode(element: XMLNode): XMLNode {
        return when {
            element.attributes.containsKey("type") -> findComplexType(element, "type")
            else -> element.childNodes.filterIsInstance<XMLNode>().filterNot { it.name == "annotation" }.first()
        }.also {
            if (it.name != "complexType")
                throw ContractException("Unexpected type node found\nSource: $element\nType: $it")
        }
    }

    fun findMessageNode(
        messageName: String
    ) =
        wsdlNode.childNodes.filterIsInstance<XMLNode>()
            .find { it.name == "message" && it.attributes["name"]?.toStringValue() == messageName }
            ?: throw ContractException(
                "Message node $messageName not found"
            )

    fun resolveNamespace(name: String): String = wsdlNode.resolveNamespace(name)

    fun getServiceName() =
        wsdlNode.findFirstChildByName("service")?.attributes?.get("name")
            ?: throw ContractException("Couldn't find attribute name in node service")

    fun getPortType() = wsdlNode.getXMLNodeByPath("portType")

    fun getBinding() = wsdlNode.getXMLNodeByPath("binding")

    fun getNamespaces(typeInfo: WSDLTypeInfo): Map<String, String> {
        return typeInfo.getNamespaces(wsdlNode.attributes)
    }

    fun mapToNamespacePrefixInDefinitions(namespacePrefix: String, element: XMLNode): String {
        val namespaceValue = element.namespaces[namespacePrefix]
            ?: throw ContractException("Can't find namespace prefix $namespacePrefix for element $element")
        return namespaceToPrefix.getValue(namespaceValue)
    }
}
