package `in`.specmatic.core.log

class ThreadSafeLog(val logger: LogStrategy) : LogStrategy by logger {
    @Synchronized
    override fun log(e: Throwable, msg: String?) {
        logger.log(e, msg)
    }

    @Synchronized
    override fun log(msg: String) {
        logger.log(msg)
    }

    @Synchronized
    override fun log(msg: LogMessage) {
        logger.log(msg)
    }

    @Synchronized
    override fun newLine() {
        logger.newLine()
    }

    @Synchronized
    override fun debug(msg: LogMessage) {
        logger.debug(msg)
    }

    @Synchronized
    override fun debug(e: Throwable, msg: String?) {
        logger.debug(e, msg)
    }

}
