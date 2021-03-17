package `in`.specmatic.core.wsdl.parser.message

import `in`.specmatic.core.pattern.XMLPattern
import `in`.specmatic.core.wsdl.parser.WSDLTypeInfo

interface ComplexTypeChild {
    // TODO perhaps existing types param and the field WSDLTypeInfo.types are duplicates?
    fun process(wsdlTypeInfo: WSDLTypeInfo, existingTypes: Map<String, XMLPattern>, typeStack: Set<String>): WSDLTypeInfo
}