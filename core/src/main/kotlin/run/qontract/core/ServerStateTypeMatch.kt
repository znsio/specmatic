package run.qontract.core

class ServerStateTypeMatch(serverState: HashMap<String, Any> = HashMap()) : ServerStateMatch(serverState) {
    override fun match(sampleValue: Any, key: String) =
            when (serverState[key] == sampleValue) {
                true -> Result.Success()
                false -> Result.Failure("ServerStateType did not match. Expected: ${serverState[key]} Actual: $sampleValue")
            }

    override fun copy() = ServerStateTypeMatch(serverState)
}