package run.qontract.core.pattern

interface Keyed {
    fun withKey(key: String?): Pattern
    val key: String?
}
