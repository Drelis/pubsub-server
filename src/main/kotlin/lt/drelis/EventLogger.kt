package lt.drelis

import java.util.logging.Logger

interface EventLogger {
    fun log(event: ServerEvent)
}

sealed class ServerEvent () {
    data class SubscriberConnected(val address: String): ServerEvent()
    data class SubscriberDisconnected(val address: String): ServerEvent()
    data class PublisherConnected(val address: String): ServerEvent()
    data class PublisherDisconnected(val address: String): ServerEvent()
    data class PublisherMessage(val from: String, val content: String): ServerEvent()
    data class Error(val from: String?, val cause: Throwable): ServerEvent()
}

class Log4jEventLogger : EventLogger {
    private val logger: Logger = Logger.getLogger(Log4jEventLogger::class.java.name)

    override fun log(event: ServerEvent) {
        when (event) {
            is ServerEvent.SubscriberConnected -> logger.info("Subscriber connected: ${event.address}")
            is ServerEvent.SubscriberDisconnected -> logger.info("Subscriber disconnected: ${event.address}")
            is ServerEvent.PublisherConnected -> logger.info("Publisher connected: ${event.address}")
            is ServerEvent.PublisherDisconnected -> logger.info("Publisher disconnected: ${event.address}")
            is ServerEvent.PublisherMessage -> logger.info("Publisher message from ${event.from}: ${event.content}")
            is ServerEvent.Error -> logger.severe("Error from ${event.from ?: "unknown"}: ${event.cause.message}\n${event.cause.stackTraceToString()}")
        }
    }
}

class MemoryEventLogger : EventLogger {
    val events = mutableListOf<ServerEvent>()

    override fun log(event: ServerEvent) {
        events += event
    }

    inline fun <reified T : ServerEvent> hasEventMatching(predicate: (T) -> Boolean): Boolean {
        return events.filterIsInstance<T>().any(predicate)
    }

    inline fun <reified T : ServerEvent> count(): Int {
        return events.count { it is T }
    }

    fun clear() {
        events.clear()
    }
}