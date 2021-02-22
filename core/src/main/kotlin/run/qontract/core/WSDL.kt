package run.qontract.core

import run.qontract.core.pattern.ContractException
import run.qontract.core.pattern.Pattern
import run.qontract.core.pattern.XMLPattern
import run.qontract.core.value.*
import java.net.URI

private const val primitiveNamespace = "http://www.w3.org/2001/XMLSchema"

private fun soapSkeleton(namespaces: Map<String, String>): XMLNode {
    val namespacesString = when(namespaces.size){
        0 -> ""
        else -> namespaces.entries
            .joinToString(" ") {
                "${it.key}=\"${it.value}\""
            }
            .prependIndent(" ")
    }
    return toXMLNode(
        """
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="$primitiveNamespace"$namespacesString>
                <soapenv:Header/>
            </soapenv:Envelope>
        """)
}

fun soapMessage(bodyPayload: XMLNode, namespaces: Map<String, String>): XMLNode {
    val payload = soapSkeleton(namespaces)
    val bodyNode = toXMLNode("<soapenv:Body/>")

    return payload.copy(childNodes = payload.childNodes.plus(bodyNode.childNodes.plus(bodyPayload)))
}

data class WSDLTypeInfo(val nodes: List<XMLValue> = emptyList(), val types: Map<String, Pattern> = emptyMap(), val namespacesPrefixes: Set<String> = emptySet())

interface SOAPParser {
    fun convertToGherkin(wsdl: XMLNode, url: String): String
}

class SOAP11Parser: SOAPParser {
    override fun convertToGherkin(wsdl: XMLNode, url: String): String {
        val binding = getXMLNodeByPath(wsdl, "binding")
        val operations = binding.findChildrenByName("operation")
        val portType = getXMLNodeByPath(wsdl, "portType")

        val operationsTypeInfo = operations.map {
            parseOperationType(it, url, wsdl, portType)
        }

        val featureName = wsdl.findFirstChildByName("service")?.attributes?.get("name") ?: throw ContractException("Couldn't find attribute name in node service")

        val featureHeading = "Feature: $featureName"

        val indent = "    "
        val gherkinScenarios = operationsTypeInfo.map { it.toGherkinScenario(indent, indent) }

        return listOf(featureHeading).plus(gherkinScenarios).joinToString("\n\n")
    }

    private fun parseOperationType(bindingOperationNode: XMLNode, url: String, wsdl: XMLNode, portType: XMLNode): SOAPOperationTypeInfo {
        val operationName = getAttributeValue(bindingOperationNode, "name")

        val soapAction = getAttributeValue(bindingOperationNode, "operation", "soapAction")

        val portOperationNode = findNodeByNameAttribute(portType, operationName)

        val requestTypeInfo = getTypeInfo(portOperationNode, operationName, "input", wsdl, emptyMap())
        val responseTypeInfo = getTypeInfo(portOperationNode, operationName, "output", wsdl, requestTypeInfo.types)

        val requestNamespaces = requestTypeInfo.namespacesPrefixes.toList().map {
            Pair(it, wsdl.attributes.getValue("xmlns:$it").toStringValue())
        }.toMap()

        val responseNamespaces = responseTypeInfo.namespacesPrefixes.toList().map {
            Pair(it, wsdl.attributes.getValue("xmlns:$it").toStringValue())
        }.toMap()

        val path = URI(url).path

        return SOAPOperationTypeInfo(path, operationName, soapAction, requestTypeInfo.typeName, responseTypeInfo.typeName, responseTypeInfo.types, requestNamespaces, responseNamespaces)
    }

    private fun getTypeInfo(portOperationNode: XMLNode, operationName: String, messageType: String, wsdl: XMLNode, existingTypes: Map<String, Pattern>): SoapPayloadTypeInfo {
        val messageName = getAttributeValue(portOperationNode, messageType, "message").withoutNamespacePrefix()
        val messageNode = wsdl.childNodes.filterIsInstance<XMLNode>().find { it.name == "message" && it.attributes["name"]?.toStringValue() == messageName } ?: throw ContractException("Message node $messageName not found")
        val wsdlTypeReference = messageNode.childNodes.filterIsInstance<XMLNode>().first().attributes["element"]?.toStringValue() ?: throw ContractException("part/element not found in message named $messageName")

        val typesNodeInWsdl = wsdl.childNodes.filterIsInstance<XMLNode>().find { it.name == "types" } ?: throw ContractException("Couldn't find types node")

        val element = findElement(
            typesNodeInWsdl,
            wsdlTypeReference.withoutNamespacePrefix(),
            wsdl.resolveNamespace(wsdlTypeReference)
        )

        val qontractTypeName = "${operationName.replace(":", "_")}${messageType.capitalize()}"
        val typeInfo = getTypes(qontractTypeName, wsdlTypeReference, element, wsdl, existingTypes, emptySet())

        return SoapPayloadTypeInfo(qontractTypeName, typeInfo.types, typeInfo.namespacesPrefixes)
    }

    private fun getTypes(qontractTypeName: String, wsdlTypeReference: String, element: XMLNode, wsdl: XMLNode, existingTypes: Map<String, Pattern>, typeStack: Set<String>): WSDLTypeInfo {
        return when {
            hasSimpleTypeAttribute(element) -> createSimpleType(element, existingTypes).let { WSDLTypeInfo(it.first, it.second) }
            qontractTypeName in typeStack -> WSDLTypeInfo(types = existingTypes)
            else -> {
                val complexType = getElementTypeNode(wsdl, element)

                val childTypeInfo = generateChildren(
                    wsdl,
                    complexType,
                    existingTypes,
                    typeStack.plus(qontractTypeName)
                )

                val isQualified = isQualified(element, wsdlTypeReference, wsdl)

                val nodeName =
                    if(isQualified)
                        wsdlTypeReference
                    else
                        wsdlTypeReference.withoutNamespacePrefix()

                val nodeTypeInfo = XMLNode(nodeName, emptyMap(), childTypeInfo.nodes)
                val inPlaceNode = toXMLNode("<qontract:$qontractTypeName/>")

                val namespacePrefix = when {
                    isQualified -> listOf(wsdlTypeReference.namespacePrefix())
                    else -> emptyList()
                }

                WSDLTypeInfo(
                    listOf(inPlaceNode),
                    existingTypes.plus(childTypeInfo.types).plus(qontractTypeName to XMLPattern(nodeTypeInfo)),
                    childTypeInfo.namespacesPrefixes.plus(namespacePrefix))
            }
        }
    }

    private fun isQualified(element: XMLNode, wsdlTypeReference: String, wsdl: XMLNode): Boolean {
        val types = getXMLNodeByName(wsdl, "types")
        val namespace = element.resolveNamespace(wsdlTypeReference)

        val schema = findSchema(types, namespace)

        val schemaElementFormDefault = schema.attributes["elementFormDefault"]?.toStringValue()
        val elementForm = element.attributes["form"]?.toStringValue()

        if(schemaElementFormDefault == "qualified" || elementForm == "qualified")
            println("NODE NAMESPACE QUALIFIED: $element")

        return (elementForm ?: schemaElementFormDefault) == "qualified"
    }

    private fun generateChildren(wsdl: XMLNode, complexType: XMLNode, existingTypes: Map<String, Pattern>, typeStack: Set<String>): WSDLTypeInfo {
        val childParts: List<XMLNode> = complexType.childNodes.filterIsInstance<XMLNode>().filterNot { it.name == "annotation" }

        return childParts.fold(WSDLTypeInfo()) { wsdlTypeInfo, child ->
            when(child.name) {
                "element" -> {
                    val wsdlTypeReference = child.attributes["type"]?.toStringValue() ?: throw ContractException("Found an element without a type attribute")

                    val qontractTypeName = wsdlTypeReference.replace(':', '_')
                    val (newNode, generatedTypes, namespacePrefixes) = getTypes(qontractTypeName, wsdlTypeReference, child, wsdl, existingTypes, typeStack)

                    val newList: List<XMLValue> = wsdlTypeInfo.nodes.plus(newNode)
                    val newTypes = wsdlTypeInfo.types.plus(generatedTypes)
                    WSDLTypeInfo(newList, newTypes, namespacePrefixes)
                }
                "sequence", "all" -> generateChildren(wsdl, child, existingTypes, typeStack)
                "complexContent" -> {
                    val extension = child.findFirstChildByName("extension") ?: throw ContractException("Found complexContent node without base attribute: $child")

                    val parentComplexType = findType(wsdl, extension, "base")
                    val (childrenFromParent, generatedTypesFromParent, namespacePrefixesFromParent) = generateChildren(wsdl, parentComplexType, existingTypes, typeStack)

                    val extensionChild = extension.childNodes.filterIsInstance<XMLNode>().filterNot { it.name == "annotation" }.getOrNull(0)
                    val (childrenFromExtensionChild, generatedTypesFromChild, namespacePrefixesFromChild) = when {
                        extensionChild != null -> generateChildren(wsdl, extensionChild, wsdlTypeInfo.types.plus(generatedTypesFromParent), typeStack)
                        else -> WSDLTypeInfo(types = generatedTypesFromParent)
                    }

                    WSDLTypeInfo(childrenFromParent.plus(childrenFromExtensionChild), generatedTypesFromChild, namespacePrefixesFromParent.plus(namespacePrefixesFromChild))
                }
                else -> throw ContractException("Couldn't recognize child node $child")
            }
        }
    }

    private fun createSimpleType(
        element: XMLNode,
        types: Map<String, Pattern>
    ): Pair<List<XMLValue>, Map<String, Pattern>> {
        val node = createSimpleType(element)
        return Pair(listOf(node), types)
    }

    private fun findNodeByNameAttribute(xmlNode: XMLNode, valueOfNameAttribute: String): XMLNode {
        return xmlNode.childNodes.filterIsInstance<XMLNode>().find {
            it.attributes["name"]?.toStringValue() == valueOfNameAttribute
        } ?: throw ContractException("Couldn't find name attribute")
    }
}

data class SoapPayloadTypeInfo(val typeName: String, val types: Map<String, Pattern>, val namespacesPrefixes: Set<String>)

data class SOAPOperationTypeInfo(
    val path: String,
    val operationName: String,
    val soapAction: String,
    val requestTypeName: String,
    val responseTypeName: String,
    val types: Map<String, Pattern>,
    val requestNamespaces: Map<String, String>,
    val responseNamespaces: Map<String, String>
) {
    fun toGherkinScenario(scenarioIndent: String = "", incrementalIndent: String = "  "): String {
        val titleStatement = listOf("Scenario: $operationName".prependIndent(scenarioIndent))

        val typeStatements = typesToGherkin(types, incrementalIndent)
        val requestStatements = requestStatements()
        val responseStatements = responseStatements()

        val statementIndent = "$scenarioIndent$incrementalIndent"
        val bodyStatements = typeStatements.plus(requestStatements).plus(responseStatements).map { it.prependIndent(statementIndent) }

        return titleStatement.plus(bodyStatements).joinToString("\n")
    }

    private fun requestStatements(): List<String> {
        val pathStatement = listOf("When POST $path")
        val soapActionHeaderStatement = when {
            soapAction.isNotBlank() -> listOf("And response-header $soapAction")
            else -> emptyList()
        }

        val requestBodyStatement = bodyPayloadStatement("request", requestNamespaces)
        return pathStatement.plus(soapActionHeaderStatement).plus(requestBodyStatement)
    }

    private fun bodyPayloadStatement(bodyType: String, namespaces: Map<String, String>): String {
        val requestBody = soapMessage(toXMLNode("<qontract:$requestTypeName/>"), namespaces)
        return "And $bodyType-body\n\"\"\"\n$requestBody\n\"\"\""
    }

    private fun responseStatements(): List<String> {
        val statusStatement = listOf("Then status 200")
        val responseBodyStatement = bodyPayloadStatement("response", responseNamespaces)
        return statusStatement.plus(responseBodyStatement)
    }

    private fun typesToGherkin(types: Map<String, Pattern>, incrementalIndent: String): List<String> {
        val typeStrings = types.entries.map { (typeName, type) ->
            if (type !is XMLPattern)
                throw ContractException("Unexpected type (name=$typeName) $type")

            val typeStringLines = type.toGherkinishXMLNode().toPrettyStringValue().trim().lines()

            val indentedTypeString = when (typeStringLines.size) {
                0 -> ""
                1 -> typeStringLines.first().trim()
                else -> {
                    val firstLine = typeStringLines.first().trim()
                    val lastLine = typeStringLines.last().trim()

                    val rest = typeStringLines.drop(1).dropLast(1).map { it.prependIndent(incrementalIndent) }

                    listOf(firstLine).plus(rest).plus(lastLine).joinToString("\n")
                }
            }

            "And type $typeName\n\"\"\"\n$indentedTypeString\n\"\"\""
        }

        return when (typeStrings.size) {
            0 -> typeStrings
            else -> {
                val firstLine = typeStrings.first().removePrefix("And ")
                val adjustedFirstLine = "Given $firstLine"

                listOf(adjustedFirstLine).plus(typeStrings.drop(1))
            }
        }
    }
}

fun findSchema(types: XMLNode, namespace: String): XMLNode {
    return when {
        namespace.isBlank() -> types.childNodes.filterIsInstance<XMLNode>().first()
        else -> {
            types.childNodes.filterIsInstance<XMLNode>().find {
                it.attributes["targetNamespace"]?.toStringValue() == namespace
            } ?: throw ContractException("Couldn't find schema with targetNamespace $namespace")
        }
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
    val namespace = if(namespacePrefix.isBlank()) "" else element.namespaces[namespacePrefix] ?: throw ContractException("Could not find namespace with prefix $namespacePrefix in xml node $element")

    return findElement(types, typeName, namespace)
}

fun hasSimpleTypeAttribute(element: XMLNode): Boolean = element.attributes.containsKey("type") && isPrimitiveType(element)

private fun createSimpleType(
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

fun isPrimitiveType(node: XMLNode): Boolean {
    val type = node.attributes.getValue("type").toStringValue()
    val namespace = node.resolveNamespace(type)

    if(namespace.isBlank())
        return primitiveTypes.contains(type)

    return namespace == primitiveNamespace
}

fun findElement(types: XMLNode, typeName: String, namespace: String): XMLNode {
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
val primitiveTypes = primitiveStringTypes.plus(primitiveNumberTypes).plus(primitiveDateTypes).plus(primitiveBooleanType)

class SOAP20Parser : SOAPParser {
    override fun convertToGherkin(wsdl: XMLNode, url: String): String {
        TODO("SOAP 2.0 is not yet implemented")
    }
}

fun convertWSDLToGherkin(wsdl: XMLNode): String {
    val port = getXMLNodeOrNull(wsdl, "service.port")
    val endpoint = getXMLNodeOrNull(wsdl, "service.endpoint")

    val (url, soapParser) = when {
        port != null -> Pair(getAttributeValue(port, "address", "location"), SOAP11Parser())
        endpoint != null -> Pair(getAttributeValue(endpoint, "address", "location"), SOAP20Parser())
        else -> throw ContractException("Could not find the service endpoint")
    }

    return soapParser.convertToGherkin(wsdl, url)
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
