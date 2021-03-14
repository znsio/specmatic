package run.qontract.core.wsdl.parser.message

interface ChildElementType {
    fun getWSDLElement(): Pair<String, WSDLElement>
}