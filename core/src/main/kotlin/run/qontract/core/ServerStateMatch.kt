package run.qontract.core

abstract class ServerStateMatch(val serverState: HashMap<String, Any> = HashMap()) {
    abstract fun match(sampleValue: Any, key: String): Result
    operator fun contains(key: String): Boolean = key in serverState
    fun get(key: String) = serverState[key]
    abstract fun copy(): ServerStateMatch
}