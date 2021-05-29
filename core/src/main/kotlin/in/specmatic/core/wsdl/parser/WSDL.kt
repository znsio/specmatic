package `in`.specmatic.core.wsdl.parser

import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.value.XMLNode
import `in`.specmatic.core.value.namespacePrefix
import `in`.specmatic.core.value.localName
import `in`.specmatic.core.wsdl.parser.message.*

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

    fun mapNamespaceToPrefix(targetNamespace: String): String {
        return namespaceToPrefix[targetNamespace] ?: throw ContractException("The target namespace $targetNamespace was not found in the WSDL definitions tag.")
    }

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
        return schema.findByNodeNameAndAttribute("complexType", "name", fullTypeName.localName())
    }

    fun findTypeFromAttribute(
        element: XMLNode,
        attributeName: String
    ): XMLNode {
        val fullTypeName = element.attributes.getValue(attributeName).toStringValue()
        return findElement(fullTypeName.localName(), namespace(fullTypeName, element))
    }

    private fun namespace(fullTypeName: String, element: XMLNode): String {
        val namespacePrefix = fullTypeName.namespacePrefix()
        return if (namespacePrefix.isBlank())
            ""
        else
            element.namespaces[namespacePrefix]
                ?: throw ContractException("Could not find namespace with prefix $namespacePrefix in xml node $element")
    }

    fun findElement(typeName: String, namespace: String): XMLNode {
        val schema = findSchema(namespace)

        return schema.getXMLNodeByAttributeValue("name", typeName)
    }

    fun getSOAPElement(wsdlTypeReference: String): WSDLElement {
        val typeName = wsdlTypeReference.localName()
        val namespace = this.resolveNamespace(wsdlTypeReference)

        val schema = findSchema(namespace)

        val node = schema.getXMLNodeByAttributeValue("name", typeName)

        return if(hasSimpleTypeAttribute(node)) {
            SimpleElement(wsdlTypeReference, node, this)
        } else {
            ComplexElement(wsdlTypeReference, node, this)
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

    fun getComplexTypeNode(element: XMLNode): ComplexType {
        val node = when {
            element.attributes.containsKey("type") -> findComplexType(element, "type")
            else -> element.childNodes.filterIsInstance<XMLNode>().filterNot { it.name == "annotation" }.first()
        }.also {
            if (it.name != "complexType")
                throw ContractException("Unexpected type node found\nSource: $element\nType: $it")
        }

        return ComplexType(node, this)
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

    fun getNamespaces(): Map<String, String> {
        return namespaceToPrefix.entries.map {
            Pair(it.value, it.key)
        }.toMap()
    }

    fun mapToNamespacePrefixInDefinitions(namespacePrefix: String, element: XMLNode): String {
        val namespaceValue = element.namespaces[namespacePrefix]
            ?: throw ContractException("Can't find namespace prefix $namespacePrefix for element $element")
        return namespaceToPrefix.getValue(namespaceValue)
    }

    fun getWSDLElementType(parentTypeName: String, node: XMLNode): ChildElementType {
        return when {
            node.attributes.containsKey("ref") -> {
                ElementReference(node, this)
            }
            node.attributes.containsKey("type") -> {
                TypeReference(node, this)
            }
            else -> {
                InlineType(parentTypeName, node, this)
            }
        }
    }

    fun getQualification(element: XMLNode, wsdlTypeReference: String): NamespaceQualification {
        val namespace = element.resolveNamespace(wsdlTypeReference)

        val schema = this.findSchema(namespace)

        val schemaElementFormDefault = schema.attributes["elementFormDefault"]?.toStringValue()
        val elementForm = element.attributes["form"]?.toStringValue()

        return when(elementForm ?: schemaElementFormDefault) {
            "qualified" -> QualifiedNamespace(element, schema, wsdlTypeReference, this)
            else -> UnqualifiedNamespace(element.getAttributeValue("name"))
        }
    }
}
