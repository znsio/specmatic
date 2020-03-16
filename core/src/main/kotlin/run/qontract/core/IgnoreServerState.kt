package run.qontract.core

class IgnoreServerState : ServerStateMatch() {
    override fun match(sampleValue: Any, key: String) = Result.Success()
    override fun copy() = this
}