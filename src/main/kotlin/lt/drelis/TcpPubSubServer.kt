package lt.drelis

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import lt.drelis.publisher.PublisherHandler
import lt.drelis.subscriber.SubscriberHandler
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections

class TcpPubSubServer(
    private val config: ServerConfig,
    private val eventLogger: EventLogger
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val subscribers = Collections.synchronizedSet(mutableSetOf<Socket>())
    private val publishers = Collections.synchronizedSet(mutableSetOf<Socket>())

    fun start() {
        scope.launch {
            listen(config.publisherPort) { socket ->
                publishers.add(socket)
                try {
                    PublisherHandler(socket, subscribers,eventLogger, config).handle()
                } finally {
                    publishers.remove(socket)
                }
            }
        }

        scope.launch {
            listen(config.subscriberPort) { socket ->
                SubscriberHandler(socket, subscribers, eventLogger, config).handle()
            }
        }
    }

    private suspend fun listen(port: Int, handle: suspend (Socket) -> Unit) {
        val server = ServerSocket(port)
        println("Listening on port $port")
        while (true) {
            val socket = server.accept()
            scope.launch { handle(socket) }
        }
    }
}