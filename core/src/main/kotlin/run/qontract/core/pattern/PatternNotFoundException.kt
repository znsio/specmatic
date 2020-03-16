package run.qontract.core.pattern

class PatternNotFoundException(val patternValue: String) : Throwable() {
    override fun toString() = "Couldn't find a match for the pattern $patternValue"
}
