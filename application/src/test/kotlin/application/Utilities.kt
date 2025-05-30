package application

import java.io.ByteArrayOutputStream
import java.io.PrintStream

fun <ReturnType> captureStandardOutput(captureStdErr: Boolean = false, fn: () -> ReturnType): Pair<String, ReturnType> {
    val originalErr = System.err
    val originalOut = System.out

    val byteArrayOutputStreamOut = ByteArrayOutputStream()
    val newOut = PrintStream(byteArrayOutputStreamOut)
    System.setOut(newOut)

    if (captureStdErr) {
        val byteArrayOutputStreamErr = ByteArrayOutputStream()
        val newErr = PrintStream(byteArrayOutputStreamErr)
        System.setErr(newErr)
    }

    val result = try {
        fn()
    } finally {
        System.setOut(originalOut)
        System.setErr(originalErr)
    }

    return Pair(String(byteArrayOutputStreamOut.toByteArray()).trim(), result)
}
