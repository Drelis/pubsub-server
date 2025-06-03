package lt.drelis.subscriber

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import lt.drelis.EventLogger
import lt.drelis.ServerConfig
import lt.drelis.ServerEvent
import java.net.Socket

class SubscriberHandler(
    private val socket: Socket,
    private val subscribers: MutableSet<Socket>,
    private val eventLogger: EventLogger,
    private val config: ServerConfig
) {
    suspend fun handle() = withContext(Dispatchers.IO) {
        val address = socket.remoteSocketAddress.toString()
        subscribers.add(socket)
        eventLogger.log(ServerEvent.SubscriberConnected(address))

        try {
            while (!socket.isClosed) {
                delay(10_000)
            }
        } catch (e: Exception) {
            eventLogger.log(ServerEvent.Error(address, e))
        } finally {
            subscribers.remove(socket)
            socket.close()
            eventLogger.log(ServerEvent.SubscriberDisconnected(address))
        }
    }
}