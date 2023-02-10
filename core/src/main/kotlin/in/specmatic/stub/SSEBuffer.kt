package `in`.specmatic.stub

import java.io.Writer

class SSEBuffer(private val buffer: MutableList<SseEvent> = mutableListOf()) {
    fun add(event: SseEvent) {
        val bufferIndex = event.bufferIndex ?: return

        if(bufferIndex == -1) {
            buffer.add(event)
        } else if(bufferIndex >= 0) {
            buffer[bufferIndex] = event
        }
    }

    fun replace(event: SseEvent, index: Int) {
        buffer[index] = event
    }

    fun write(writer: Writer) {
        for(event in buffer) {
            writeEvent(event, writer)
        }
    }
}