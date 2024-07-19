package application

import io.specmatic.core.log.logger
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket


fun portIsInUse(host: String, port: Int): Boolean {
    return try {
        val ipAddress = InetAddress.getByName(host)
        ServerSocket(port, 1, ipAddress).use {
            false
        }
    } catch (e: IOException) {
        true
    }
}

fun findRandomFreePort(): Int {
    logger.log("Checking for a free port")

    val port = ServerSocket(0).use { it.localPort }

    if (port > 0) {
        logger.log("Free port found: $port")
        return port
    }
    throw RuntimeException("Could not find a free port")
}

