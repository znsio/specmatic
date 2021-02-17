package run.qontract.core

import run.qontract.core.pattern.ContractException
import run.qontract.core.value.*
import run.qontract.mock.ScenarioStub

private const val primitiveNamespace = "http://www.w3.org/2001/XMLSchema"

private fun soapSkeleton() = toXMLNode(
    """
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="$primitiveNamespace">
                <soapenv:Header/>
            </soapenv:Envelope>
        """)

data class SOAPOperation(val name: String, val soapAction: String, val requestPayloadType: SOAPType, val responsePayloadType: SOAPType) {
    fun getRequestPayload(wsdl: XMLNode): XMLNode {
        val requestBody = requestPayloadType.getPayload(wsdl)
        return soapMessage(requestBody)
    }


    fun getResponsePayload(wsdl: XMLNode): XMLNode {
        val responseBody = responsePayloadType.getPayload(wsdl)
        return soapMessage(responseBody)
    }
}

fun soapMessage(bodyPayload: XMLNode): XMLNode {
    val payload = soapSkeleton()
    val bodyNode = toXMLNode("<soapenv:Body/>")

    return payload.copy(childNodes = payload.childNodes.plus(bodyNode.childNodes.plus(bodyPayload)))
}

interface SOAPParser {
    fun getOperations(wsdl: XMLNode): List<SOAPOperation>
}

class SOAP11Parser: SOAPParser {
    override fun getOperations(wsdl: XMLNode): List<SOAPOperation> {
        val binding = getXMLNodeByPath(wsdl, "binding")
        val operations = binding.findChildrenByName("operation")
        val portType = getXMLNodeByPath(wsdl, "portType")

        return operations.map { bindingOperationNode ->
            val operationName = getAttributeValue(bindingOperationNode, "name")

            val soapAction = getAttributeValue(bindingOperationNode, "operation", "soapAction")

            val portOperationNode = findNodeByNameAttribute(portType, operationName)

            val requestPayloadName = getAttributeValue(portOperationNode, "input", "message").withoutNamespacePrefix()
            val responsePayloadName = getAttributeValue(portOperationNode, "output", "message").withoutNamespacePrefix()

            val requestPayloadType = getPayloadElementNode(wsdl, requestPayloadName)
            val responsePayloadType = getPayloadElementNode(wsdl, responsePayloadName)

            SOAPOperation(operationName, soapAction, requestPayloadType, responsePayloadType)
        }
    }

    private fun getPayloadElementNode(wsdl: XMLNode, requestPayloadName: String): SOAPType {
        val messageNode = wsdl.childNodes.filterIsInstance<XMLNode>().find {
            it.name == "message" && it.attributes["name"]?.toStringValue() == requestPayloadName
        } ?: throw ContractException("Could not find request message")

        val part = getXMLNodeByPath(messageNode, "part")

        return part.messageType(messageNode)
    }

    private fun findNodeByNameAttribute(xmlNode: XMLNode, valueOfNameAttribute: String): XMLNode {
        return xmlNode.childNodes.filterIsInstance<XMLNode>().find {
            it.attributes["name"]?.toStringValue() == valueOfNameAttribute
        } ?: throw ContractException("Couldn't find name attribute")
    }
}

fun XMLNode.messageType(messageNode: XMLNode) = when {
    hasSimpleTypeAttribute(this) -> SimpleSOAPType(this)
    else -> toComplexSOAPType(messageNode, this)
}

interface SOAPType {
    fun getPayload(wsdl: XMLNode): XMLNode
}

fun findSchema(types: XMLNode, payloadNamespace: String): XMLNode {
    return types.childNodes.filterIsInstance<XMLNode>().find {
        it.attributes["targetNamespace"]?.toStringValue() == payloadNamespace
    } ?: throw ContractException("Couldn't find schema with targetNamespace $payloadNamespace")
}

fun generateNodeFromElement(wsdl: XMLNode, element: XMLNode, typeStack: Map<String, Int>): XMLValue {
    return if(hasSimpleTypeAttribute(element))
        createUsingTypeAttribute(element)
    else {
        val complexType = getElementTypeNode(wsdl, element)

        val updatedTypeStack =
            if(element.attributes.containsKey("type")) {
                val type = element.attributes.getValue("type").toStringValue()
                val currentValue = typeStack.getOrDefault(type, 0)
                typeStack.plus(type to currentValue + 1)
            } else
                typeStack

        val children: List<XMLValue> = generateChildren(wsdl, complexType, updatedTypeStack)
        XMLNode(getAttributeValue(element, "name").withoutNamespacePrefix(), emptyMap(), children)
    }
}

fun getElementTypeNode(wsdl: XMLNode, element: XMLNode): XMLNode {
    return when {
        element.attributes.containsKey("type") -> findType(wsdl, element)
        else -> element.childNodes.filterIsInstance<XMLNode>().filterNot { it.name == "annotation" }.first()
    }.also {
        if(it.name != "complexType")
            throw ContractException("Unexpected type node found: $it")
    }
}

fun findType(
    wsdl: XMLNode,
    element: XMLNode
): XMLNode = findType(wsdl, element, "type")

fun findType(
    wsdl: XMLNode,
    element: XMLNode,
    attributeName: String
): XMLNode {
    val types = getXMLNodeByName(wsdl, "types")

    val fullTypeName = element.attributes.getValue(attributeName).toStringValue()
    val typeName = fullTypeName.withoutNamespacePrefix()
    val namespacePrefix = fullTypeName.namespacePrefix()
    val namespace = element.namespaces[namespacePrefix] ?: throw ContractException("Could not find namespace with prefix $namespacePrefix in xml node $element")

    return findType(types, typeName, namespace)
}

fun hasSimpleTypeAttribute(element: XMLNode): Boolean = element.attributes.containsKey("type") && isPrimitiveType(element)

private fun createUsingTypeAttribute(
    element: XMLNode
): XMLNode {
    val typeName = element.attributes.getValue("type").toStringValue().withoutNamespacePrefix()
    val value = when {
        primitiveStringTypes.contains(typeName) -> StringValue("(string)")
        primitiveNumberTypes.contains(typeName) -> StringValue("(number)")
        primitiveDateTypes.contains(typeName) -> StringValue("(datetime)")
        primitiveBooleanType.contains(typeName) -> StringValue("(boolean)")
        else -> throw ContractException("""Primitive type "$typeName" not recognized""")
    }

    return XMLNode(getAttributeValue(element, "name").withoutNamespacePrefix(), emptyMap(), listOf(value))
}

fun ifStackWontOverflow(element: XMLNode, typeStack: Map<String, Int>, fn: () -> List<XMLValue>): List<XMLValue> {
    val type = element.attributes["type"]?.toStringValue()

    return when {
        type != null && typeStack.getOrDefault(type, 0) > 1 -> emptyList()
        else -> fn()
    }
}

private fun generateChildren(wsdl: XMLNode, complexType: XMLNode, typeStack: Map<String, Int>): List<XMLValue> {
    val childParts = complexType.childNodes.filterIsInstance<XMLNode>().filterNot { it.name == "annotation" }

    return childParts.map { childPart ->
        when(childPart.name) {
            "element" -> ifStackWontOverflow(childPart, typeStack) { listOf(generateNodeFromElement(wsdl, childPart, typeStack)) }
            "sequence" -> generateChildren(wsdl, childPart, typeStack)
            "complexContent" -> {
                val extension = childPart.findFirstChildByName("extension") ?: throw ContractException("Found complexContent node without base attribute: $childPart")

                val parentComplexType = findType(wsdl, extension, "base")
                val childrenFromParent = generateChildren(wsdl, parentComplexType, typeStack)

                val extensionChild = extension.childNodes.filterIsInstance<XMLNode>().filterNot { it.name == "annotation" }.getOrNull(0)
                val childrenFromExtensionChild = if(extensionChild != null) generateChildren(wsdl, extensionChild, typeStack) else emptyList()

                childrenFromParent.plus(childrenFromExtensionChild)
            }
            else -> throw ContractException("Couldn't recognize child node $childPart")
        }
    }.flatten()
}

data class ElementType(val typeName: String, val namespace: String) : SOAPType {
    override fun getPayload(wsdl: XMLNode): XMLNode {
        val types = getXMLNodeByName(wsdl, "types")
        val element = findType(types, typeName.withoutNamespacePrefix(), namespace)
        return generateNodeFromElement(wsdl, element, emptyMap()) as XMLNode
    }
}

fun isPrimitiveType(node: XMLNode): Boolean {
    val type = node.attributes.getValue("type").toStringValue()
    return node.resolveNamespaceFromName(type) == primitiveNamespace
}

fun findType(types: XMLNode, typeName: String, namespace: String): XMLNode {
    val schema = findSchema(types, namespace)
    return getXMLNodeByAttributeValue(schema, "name", typeName)
}

fun getXMLNodeByAttributeValue(schema: XMLNode, attributeName: String, typeName: String): XMLNode {
    return schema.childNodes.filterIsInstance<XMLNode>().find {
        it.attributes[attributeName]?.toStringValue() == typeName
    } ?: throw ContractException("Couldn't find in the given schema a node with attribute $attributeName=$typeName")
}

val primitiveStringTypes = listOf("string", "duration", "time", "date", "gYearMonth", "gYear", "gMonthDay", "gDay", "gMonth", "hexBinary", "base64Binary", "anyURI", "QName", "NOTATION")
val primitiveNumberTypes = listOf("int", "integer", "decimal", "float", "double")
val primitiveDateTypes = listOf("dateTime")
val primitiveBooleanType = listOf("boolean")

data class SimpleSOAPType(val typeName: String) : SOAPType {
    constructor(stringValue: StringValue): this(stringValue.toStringValue().withoutNamespacePrefix())
    constructor(part: XMLNode) : this(part.attributes["type"]!!)

    override fun getPayload(wsdl: XMLNode): XMLNode {
        TODO("Not yet implemented")
    }
}

fun toComplexSOAPType(messageNode: XMLNode, part: XMLNode): ElementType {
    val requestPayloadElementTypeName = getAttributeValue(part, "element")
    val requestPayloadNamespace: String =
        messageNode.namespaces[requestPayloadElementTypeName.namespacePrefix()]
            ?: throw ContractException("Could not find namespace prefix ${requestPayloadElementTypeName.namespacePrefix()}")
    return ElementType(requestPayloadElementTypeName, requestPayloadNamespace)
}

class SOAP20Parser : SOAPParser {
    override fun getOperations(wsdl: XMLNode): List<SOAPOperation> {
        TODO("SOAP 2.0 parser is not yet implemented")
    }

}

fun parseWSDLIntoScenarios(wsdl: XMLNode): List<NamedStub> {
    val port = getXMLNodeOrNull(wsdl, "service.port")
    val endpoint = getXMLNodeOrNull(wsdl, "service.endpoint")

    val (url, soapParser) = when {
        port != null -> Pair(getAttributeValue(port, "address", "location"), SOAP11Parser())
        endpoint != null -> Pair(getAttributeValue(endpoint, "address", "location"), SOAP20Parser())
        else -> throw ContractException("Could not find the service endpoint")
    }

    val operations = soapParser.getOperations(wsdl)

    return operations.map {
        val method = "POST"

        val requestBody = it.getRequestPayload(wsdl)

        val request = HttpRequest(method = method, path = url, headers = mapOf("Content-Type" to "application/xml"), body = requestBody)
        val response = HttpResponse.OK(it.getResponsePayload(wsdl))

        NamedStub(it.name, ScenarioStub(request, response))
    }
}

private fun getAttributeValue(node: XMLNode, path: String, attributeName: String): String {
    val childNode = getXMLNodeByPath(node, path)
    return childNode.attributes[attributeName]?.toStringValue() ?: throw ContractException("Couldn't find attribute $attributeName at path $path")
}

private fun getAttributeValue(node: XMLNode, attributeName: String): String {
    return node.attributes[attributeName]?.toStringValue() ?: throw ContractException("Couldn't find attribute $attributeName in node $node")
}

private fun getXMLNodeByPath(node: XMLNode, path: String): XMLNode =
    node.findFirstChildByPath(path) ?: throw ContractException("Couldn't find node at path $path")

private fun getXMLNodeOrNull(node: XMLNode, path: String): XMLNode? =
    node.findFirstChildByPath(path)

private fun getXMLNodeByName(node: XMLNode, name: String): XMLNode =
    node.findFirstChildByName(name) ?: throw ContractException("Couldn't find node named $name")
