package application

import java.io.ByteArrayOutputStream
import java.io.PrintStream

fun <ReturnType> captureStandardOutput(fn: () -> ReturnType): Pair<String, ReturnType> {
    val originalOut = System.out

    val byteArrayOutputStream = ByteArrayOutputStream()
    val newOut = PrintStream(byteArrayOutputStream)
    System.setOut(newOut)

    val result = fn()

    System.out.flush()
    System.setOut(originalOut) // So you can print again
    return Pair(String(byteArrayOutputStream.toByteArray()).trim(), result)
}
