package application

import `in`.specmatic.core.log.logger
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

    var serverSocket: ServerSocket? = null

    val port = try {
        serverSocket = ServerSocket(0)
        serverSocket.localPort
    } finally {
        serverSocket?.close()
    }

    if (port > 0) {
        logger.log("Free port found: $port")
        return port
    }
    throw RuntimeException("Could not find a free port")
}

