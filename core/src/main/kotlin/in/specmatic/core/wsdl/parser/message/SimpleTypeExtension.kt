package `in`.specmatic.core.wsdl.parser.message

import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.pattern.XMLPattern
import `in`.specmatic.core.value.XMLNode
import `in`.specmatic.core.value.XMLValue
import `in`.specmatic.core.wsdl.parser.WSDL
import `in`.specmatic.core.wsdl.parser.WSDLTypeInfo

class SimpleTypeExtension(private var simpleTypeNode: XMLNode, var wsdl: WSDL) : ComplexTypeChild {
    override fun process(
        wsdlTypeInfo: WSDLTypeInfo,
        existingTypes: Map<String, XMLPattern>,
        typeStack: Set<String>
    ): WSDLTypeInfo {
        val extension = simpleTypeNode.findFirstChildByName("extension", "Node ${simpleTypeNode.realName} does not have a child node named extension")

        val simpleType = wsdl.findSimpleType(extension, "base") ?: throw ContractException("Type with name in base of node ${extension.name} could not be found")

        val simpleTypeInfo = createSimpleType(simpleType, wsdl).let { (nodes, prefix) ->
            toTypeInfo(prefix, nodes, existingTypes)
        }

        return wsdlTypeInfo.plus(simpleTypeInfo)
    }

    private fun toTypeInfo(
        prefix: String?,
        nodes: List<XMLValue>,
        existingTypes: Map<String, XMLPattern>
    ) = if (prefix != null) {
        WSDLTypeInfo(nodes = nodes, existingTypes, setOf(prefix))
    } else {
        WSDLTypeInfo(nodes, existingTypes)
    }
}
