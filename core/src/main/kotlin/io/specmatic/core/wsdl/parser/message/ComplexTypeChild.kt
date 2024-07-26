package io.specmatic.core.wsdl.parser.message

import io.specmatic.core.pattern.XMLPattern
import io.specmatic.core.wsdl.parser.WSDLTypeInfo

interface ComplexTypeChild {
    // TODO perhaps existing types param and the field WSDLTypeInfo.types are duplicates?
    fun process(wsdlTypeInfo: WSDLTypeInfo, existingTypes: Map<String, XMLPattern>, typeStack: Set<String>): WSDLTypeInfo
}