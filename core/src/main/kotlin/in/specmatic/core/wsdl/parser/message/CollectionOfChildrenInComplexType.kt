package `in`.specmatic.core.wsdl.parser.message

import `in`.specmatic.core.pattern.XMLPattern
import `in`.specmatic.core.value.XMLNode
import `in`.specmatic.core.wsdl.parser.WSDL
import `in`.specmatic.core.wsdl.parser.WSDLTypeInfo

internal class CollectionOfChildrenInComplexType(private val parent: ComplexElement, private val child: XMLNode, val wsdl: WSDL, private val parentTypeName: String):
    ComplexTypeChild {
    override fun process(wsdlTypeInfo: WSDLTypeInfo, existingTypes: Map<String, XMLPattern>, typeStack: Set<String>): WSDLTypeInfo =
        parent.generateChildren(parentTypeName, child, existingTypes, typeStack)

}