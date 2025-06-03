package lt.drelis.publisher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lt.drelis.EventLogger
import lt.drelis.ServerConfig
import lt.drelis.ServerEvent
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket

class PublisherHandler(
        private val socket: Socket,
        private val subscribers: Set<Socket>,
        private val eventLogger: EventLogger,
        private val config: ServerConfig
) {
        suspend fun handle() = withContext(Dispatchers.IO) {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
                val address = socket.remoteSocketAddress.toString()

                try {
                        val initialMessage = if (subscribers.isEmpty()) {
                                config.noSubscribersMessage
                        } else {
                                config.subscriberCountMessage.replace("{count}", subscribers.size.toString())
                        }

                        writer.write(initialMessage + "\n")
                        writer.flush()

                        eventLogger.log(ServerEvent.PublisherConnected(address))

                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                                val messageToSend = line!! + "\n"
                                eventLogger.log(ServerEvent.PublisherMessage(address, line!!))

                                synchronized(subscribers) {
                                        subscribers.forEach { sub ->
                                                try {
                                                        val subWriter = BufferedWriter(OutputStreamWriter(sub.getOutputStream()))
                                                        subWriter.write(messageToSend)
                                                        subWriter.flush()
                                                } catch (e: IOException) {
                                                        eventLogger.log(ServerEvent.Error(sub.remoteSocketAddress.toString(), e))
                                                }
                                        }
                                }
                        }
                } catch (e: Exception) {
                        eventLogger.log(ServerEvent.Error(address, e))
                } finally {
                        socket.close()
                        eventLogger.log(ServerEvent.PublisherDisconnected(address))
                }
        }
}
