package io.specmatic.core.wsdl.parser.message

import io.specmatic.core.pattern.XMLPattern
import io.specmatic.core.value.XMLNode
import io.specmatic.core.wsdl.parser.WSDL
import io.specmatic.core.wsdl.parser.WSDLTypeInfo

internal class CollectionOfChildrenInComplexType(
    private val child: XMLNode,
    val wsdl: WSDL,
    private val parentTypeName: String
):
    ComplexTypeChild {
    override fun process(wsdlTypeInfo: WSDLTypeInfo, existingTypes: Map<String, XMLPattern>, typeStack: Set<String>): WSDLTypeInfo =
        generateChildren(parentTypeName, child, existingTypes, typeStack, wsdl)

}