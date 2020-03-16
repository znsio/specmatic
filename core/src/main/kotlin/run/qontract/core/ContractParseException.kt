package run.qontract.core

class ContractParseException(override val message: String) : Throwable() {
    override fun toString() = message
}