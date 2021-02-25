package run.qontract.core.wsdl

import run.qontract.core.pattern.ContractException
import run.qontract.core.value.XMLNode
import run.qontract.core.value.namespacePrefix
import run.qontract.core.value.withoutNamespacePrefix

data class WSDL(private val wsdlNode: XMLNode, private val typesNode: XMLNode) {
    constructor(wsdl: XMLNode) : this (wsdl, wsdl.getXMLNodeByName("types"))

    fun convertToGherkin(): String {
        val port = wsdlNode.getXMLNodeOrNull("service.port")
        val endpoint = wsdlNode.getXMLNodeOrNull("service.endpoint")

        val (url, soapParser) = when {
            port != null -> Pair(port.getAttributeValue("address", "location"), SOAP11Parser())
            endpoint != null -> Pair(endpoint.getAttributeValue("address", "location"), SOAP20Parser())
            else -> throw ContractException("Could not find the service endpoint")
        }

        return soapParser.convertToGherkin(this, url)
    }

    fun findType(element: XMLNode): XMLNode = findType(element, "type")

    fun findType(
        element: XMLNode,
        attributeName: String
    ): XMLNode {
        val fullTypeName = element.attributes.getValue(attributeName).toStringValue()
        val typeName = fullTypeName.withoutNamespacePrefix()
        val namespacePrefix = fullTypeName.namespacePrefix()
        val namespace = if(namespacePrefix.isBlank()) "" else element.namespaces[namespacePrefix] ?: throw ContractException("Could not find namespace with prefix $namespacePrefix in xml node $element")

        return findElement(typeName, namespace)
    }

    fun findElement(typeName: String, namespace: String): XMLNode {
        val schema = findSchema(namespace)

        return schema.getXMLNodeByAttributeValue("name", typeName)
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

    fun getElementTypeNode(element: XMLNode): XMLNode {
        return when {
            element.attributes.containsKey("type") -> findType(element)
            else -> element.childNodes.filterIsInstance<XMLNode>().filterNot { it.name == "annotation" }.first()
        }.also {
            if(it.name != "complexType")
                throw ContractException("Unexpected type node found: $it")
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

    fun namespacePrefixMap(
        namespacesPrefixes: Set<String>
    ) = namespacesPrefixes.toList().map {
        Pair(it, wsdlNode.attributes.getValue("xmlns:$it").toStringValue())
    }.toMap()

    fun getServiceName() =
        wsdlNode.findFirstChildByName("service")?.attributes?.get("name")
            ?: throw ContractException("Couldn't find attribute name in node service")

    fun getPortType() = wsdlNode.getXMLNodeByPath("portType")

    fun getBinding() = wsdlNode.getXMLNodeByPath("binding")

}
