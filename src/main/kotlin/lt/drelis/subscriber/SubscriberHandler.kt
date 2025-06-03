package lt.drelis.subscriber

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lt.drelis.EventLogger
import lt.drelis.ServerEvent
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket

class SubscriberHandler(
    private val socket: Socket,
    private val subscribers: MutableSet<Socket>,
    private val eventLogger: EventLogger
) {
    suspend fun handle() = withContext(Dispatchers.IO) {
        val address = socket.remoteSocketAddress.toString()
        subscribers.add(socket)
        eventLogger.log(ServerEvent.SubscriberConnected(address))

        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

        try {
            while (true) {
                val line = reader.readLine() ?: break
                if (line.equals("ping", ignoreCase = true)) {
                    socket.getOutputStream().write("pong\n".toByteArray())
                } else {
                    eventLogger.log(ServerEvent.Error(
                        "Received unexpected from subscriber: '$line'",
                        IllegalStateException()
                    ))
                }
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