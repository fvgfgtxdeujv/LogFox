package com.f0x1d.logfox.mcp.impl.sse

object SseFormatter {

    fun format(event: SseEvent): String {
        val sb = StringBuilder()

        event.event?.let {
            sb.append("event: ").append(it).append('\n')
        }

        event.id?.let {
            sb.append("id: ").append(it).append('\n')
        }

        event.retry?.let {
            sb.append("retry: ").append(it).append('\n')
        }

        val lines = event.data.split('\n')
        lines.forEachIndexed { index, line ->
            if (index == 0) {
                sb.append("data: ")
            } else {
                sb.append('\n').append("data: ")
            }
            sb.append(line)
        }

        sb.append("\n\n")

        return sb.toString()
    }
}
