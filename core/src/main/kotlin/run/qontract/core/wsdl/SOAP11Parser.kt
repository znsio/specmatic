package run.qontract.core.wsdl

import run.qontract.core.pattern.ContractException
import run.qontract.core.pattern.Pattern
import run.qontract.core.pattern.XMLPattern
import run.qontract.core.value.*
import java.net.URI

class SOAP11Parser: SOAPParser {
    override fun convertToGherkin(wsdl: WSDL, url: String): String {
        val binding = wsdl.getBinding()
        val operations = binding.findChildrenByName("operation")
        val portType = wsdl.getPortType()

        val operationsTypeInfo = operations.map {
            parseOperationType(it, url, wsdl, portType)
        }

        val featureName = wsdl.getServiceName()

        val featureHeading = "Feature: $featureName"

        val indent = "    "
        val gherkinScenarios = operationsTypeInfo.map { it.toGherkinScenario(indent, indent) }

        return listOf(featureHeading).plus(gherkinScenarios).joinToString("\n\n")
    }

    private fun parseOperationType(bindingOperationNode: XMLNode, url: String, wsdl: WSDL, portType: XMLNode): SOAPOperationTypeInfo {
        val operationName = bindingOperationNode.getAttributeValue("name")

        val soapAction = bindingOperationNode.getAttributeValue("operation", "soapAction")

        val portOperationNode = findNodeByNameAttribute(portType, operationName)

        val requestTypeInfo = getTypeInfo(
            portOperationNode,
            operationName,
            SOAPMessageType.Input,
            wsdl,
            emptyMap()
        )

        val responseTypeInfo = getTypeInfo(
            portOperationNode,
            operationName,
            SOAPMessageType.Output,
            wsdl,
            requestTypeInfo.types
        )

        val path = URI(url).path

        return SOAPOperationTypeInfo(
            path,
            operationName,
            soapAction,
            responseTypeInfo.types,
            requestTypeInfo.soapPayload,
            responseTypeInfo.soapPayload
        )
    }

    private fun getTypeInfo(
        portOperationNode: XMLNode,
        operationName: String,
        soapMessageType: SOAPMessageType,
        wsdl: WSDL,
        existingTypes: Map<String, Pattern>
    ): SoapPayloadType {
        val messageName = portOperationNode.getAttributeValue(soapMessageType.messageTypeName, "message").withoutNamespacePrefix()
        val messageNode = wsdl.findMessageNode(messageName)
        val wsdlTypeReference = messageNode.firstNode().attributes["element"]?.toStringValue() ?: throw ContractException(
            "part/element not found in message named $messageName"
        )

        val element = wsdl.findElement(
            wsdlTypeReference.withoutNamespacePrefix(),
            wsdl.resolveNamespace(wsdlTypeReference)
        )

        val qontractTypeName = "${operationName.replace(":", "_")}${soapMessageType.messageTypeName.capitalize()}"
        val typeInfo = getQontractTypes(qontractTypeName, wsdlTypeReference, element, wsdl, existingTypes, emptySet())
        val namespaces: Map<String, String> = wsdl.getNamespaces(typeInfo)
        val soapPayload = NormalSOAPPayload(soapMessageType, qontractTypeName, namespaces)

        return SoapPayloadType(typeInfo.types, soapPayload)
    }

    private fun getQontractTypes(qontractTypeName: String, wsdlTypeReference: String, element: XMLNode, wsdl: WSDL, existingTypes: Map<String, Pattern>, typeStack: Set<String>): WSDLTypeInfo {
        return when {
            hasSimpleTypeAttribute(element) -> createSimpleType(element, existingTypes).let {
                WSDLTypeInfo(
                    it.first,
                    it.second
                )
            }
            qontractTypeName in typeStack -> WSDLTypeInfo(types = existingTypes)
            else -> {
                val complexType = wsdl.getElementTypeNode(element)

                val childTypeInfo = generateChildren(
                    wsdl,
                    complexType,
                    existingTypes,
                    typeStack.plus(qontractTypeName)
                )

                val isQualified = isQualified(element, wsdlTypeReference, wsdl)
                val nodeName = element.getAttributeValue("name")

                val qualifiedNodeName =
                    if(isQualified)
                        "${wsdlTypeReference.namespacePrefix()}:${nodeName.withoutNamespacePrefix()}"
                    else
                        nodeName

                val attributes: Map<String, StringValue> = getQontractAttributes(element)

                val nodeTypeInfo = XMLNode(qualifiedNodeName, attributes, childTypeInfo.nodes)
                val inPlaceNode = toXMLNode("<$XML_TYPE_PREFIX$qontractTypeName/>")

                val namespacePrefix = when {
                    isQualified -> listOf(wsdlTypeReference.namespacePrefix())
                    else -> emptyList()
                }

                WSDLTypeInfo(
                    listOf(inPlaceNode),
                    existingTypes.plus(childTypeInfo.types).plus(qontractTypeName to XMLPattern(nodeTypeInfo)),
                    childTypeInfo.namespacesPrefixes.plus(namespacePrefix)
                )
            }
        }
    }

    private fun getQontractAttributes(element: XMLNode): Map<String, StringValue> {
        return when {
            elementIsOptional(element) -> mapOf(OCCURS_ATTRIBUTE_NAME to StringValue(OPTIONAL_ATTRIBUTE_VALUE))
            multipleElementsCanExist(element) -> mapOf(OCCURS_ATTRIBUTE_NAME to StringValue(MULTIPLE_ATTRIBUTE_VALUE))
            else -> emptyMap()
        }
    }

    private fun multipleElementsCanExist(element: XMLNode): Boolean {
        return element.attributes.containsKey("maxOccurs")
                && (element.attributes["maxOccurs"]?.toStringValue() == "unbounded"
                || element.attributes.getValue("maxOccurs").toStringValue().toInt() > 1)
    }

    private fun elementIsOptional(element: XMLNode): Boolean {
        return element.attributes["minOccurs"]?.toStringValue() == "0" && !element.attributes.containsKey("maxOccurs")
    }

    private fun isQualified(element: XMLNode, wsdlTypeReference: String, wsdl: WSDL): Boolean {
        val namespace = element.resolveNamespace(wsdlTypeReference)

        val schema = wsdl.findSchema(namespace)

        val schemaElementFormDefault = schema.attributes["elementFormDefault"]?.toStringValue()
        val elementForm = element.attributes["form"]?.toStringValue()

        return (elementForm ?: schemaElementFormDefault) == "qualified"
    }

    private fun generateChildren(wsdl: WSDL, complexType: XMLNode, existingTypes: Map<String, Pattern>, typeStack: Set<String>): WSDLTypeInfo {
        val childParts: List<XMLNode> = complexType.childNodes.filterIsInstance<XMLNode>().filterNot { it.name == "annotation" }

        return childParts.fold(WSDLTypeInfo()) { wsdlTypeInfo, child ->
            when(child.name) {
                "element" -> {
                    val wsdlTypeReference = child.attributes["type"]?.toStringValue() ?: throw ContractException("Found an element without a type attribute")

                    val qontractTypeName = wsdlTypeReference.replace(':', '_')
                    val (newNode, generatedTypes, namespacePrefixes) = getQontractTypes(qontractTypeName, wsdlTypeReference, child, wsdl, existingTypes, typeStack)

                    val newList: List<XMLValue> = wsdlTypeInfo.nodes.plus(newNode)
                    val newTypes = wsdlTypeInfo.types.plus(generatedTypes)
                    WSDLTypeInfo(newList, newTypes, namespacePrefixes)
                }
                "sequence", "all" -> generateChildren(wsdl, child, existingTypes, typeStack)
                "complexContent" -> {
                    val extension = child.findFirstChildByName("extension") ?: throw ContractException("Found complexContent node without base attribute: $child")

                    val parentComplexType = wsdl.findType(extension, "base")
                    val (childrenFromParent, generatedTypesFromParent, namespacePrefixesFromParent) = generateChildren(wsdl, parentComplexType, existingTypes, typeStack)

                    val extensionChild = extension.childNodes.filterIsInstance<XMLNode>().filterNot { it.name == "annotation" }.getOrNull(0)
                    val (childrenFromExtensionChild, generatedTypesFromChild, namespacePrefixesFromChild) = when {
                        extensionChild != null -> generateChildren(wsdl, extensionChild, wsdlTypeInfo.types.plus(generatedTypesFromParent), typeStack)
                        else -> WSDLTypeInfo(types = generatedTypesFromParent)
                    }

                    WSDLTypeInfo(
                        childrenFromParent.plus(childrenFromExtensionChild),
                        generatedTypesFromChild,
                        namespacePrefixesFromParent.plus(namespacePrefixesFromChild)
                    )
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

private fun soapSkeleton(namespaces: Map<String, String>): XMLNode {
    val namespacesString = when(namespaces.size){
        0 -> ""
        else -> namespaces.entries
            .joinToString(" ") {
                "xmlns:${it.key}=\"${it.value}\""
            }
            .prependIndent(" ")
    }
    return toXMLNode(
        """
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="$primitiveNamespace"$namespacesString><soapenv:Header $OCCURS_ATTRIBUTE_NAME="$OPTIONAL_ATTRIBUTE_VALUE"/>
            </soapenv:Envelope>
        """)
}

fun soapMessage(bodyPayload: XMLNode, namespaces: Map<String, String>): XMLNode {
    val payload = soapSkeleton(namespaces)
    val bodyNode = toXMLNode("<soapenv:Body/>").let {
        it.copy(childNodes = it.childNodes.plus(bodyPayload))
    }

    return payload.copy(childNodes = payload.childNodes.plus(bodyNode))
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

    return XMLNode(element.getAttributeValue("name").withoutNamespacePrefix(), emptyMap(), listOf(value))
}

fun isPrimitiveType(node: XMLNode): Boolean {
    val type = node.attributes.getValue("type").toStringValue()
    val namespace = node.resolveNamespace(type)

    if(namespace.isBlank())
        return primitiveTypes.contains(type)

    return namespace == primitiveNamespace
}

val primitiveStringTypes = listOf("string", "duration", "time", "date", "gYearMonth", "gYear", "gMonthDay", "gDay", "gMonth", "hexBinary", "base64Binary", "anyURI", "QName", "NOTATION")
val primitiveNumberTypes = listOf("int", "integer", "long", "decimal", "float", "double")
val primitiveDateTypes = listOf("dateTime")
val primitiveBooleanType = listOf("boolean")
val primitiveTypes = primitiveStringTypes.plus(primitiveNumberTypes).plus(primitiveDateTypes).plus(primitiveBooleanType)

private const val primitiveNamespace = "http://www.w3.org/2001/XMLSchema"
const val XML_TYPE_PREFIX = "qontract_"

const val OCCURS_ATTRIBUTE_NAME = "qontract_occurs"
const val OPTIONAL_ATTRIBUTE_VALUE = "optional"
const val MULTIPLE_ATTRIBUTE_VALUE = "multiple"
