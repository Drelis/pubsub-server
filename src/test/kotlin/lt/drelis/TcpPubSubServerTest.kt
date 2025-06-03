package lt.drelis

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TcpPubSubServerTest {
    private lateinit var server: TcpPubSubServer
    private val config = ServerConfig(
        publisherPort = 9111,
        subscriberPort = 9222,
        noSubscribersMessage = "No subscribers connected",
        subscriberCountMessage = "{count} subscriber(s) connected"
    )
    private val openSockets = mutableListOf<Socket>()
    private val logger = MemoryEventLogger()

    @BeforeAll
    fun setup() {
        server = TcpPubSubServer(config, logger)
        server.start()
        Thread.sleep(500)
    }

    @AfterEach
    fun cleanupSockets() {
        openSockets.forEach {
            try {
                it.close()
            } catch (_: Exception) {}
        }
        openSockets.clear()
        logger.clear()
        runBlocking { delay(100) }
    }

    @Test
    fun whenPublisherConnects_thenNoSubscribersAvailableMessageIsReturned() = runBlocking {
        waitUntil {
            logger.events.any { it is ServerEvent.SubscriberDisconnected }
        }

        val socket = Socket("localhost", config.publisherPort)
        openSockets += socket
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        val initial = reader.readLine()
        assertEquals("No subscribers connected", initial)
    }

    @Test
    fun whenPublisherConnects_thenSubscriberListIsReturned() = runBlocking {
        val subscriber = Socket("localhost", config.subscriberPort)
        openSockets += subscriber
        delay(100)
        subscriber.close()

        waitUntil(timeoutMs = 2000) {
            logger.events.any { it is ServerEvent.SubscriberDisconnected }
        }

        val socket = Socket("localhost", config.publisherPort)
        openSockets += socket
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        val initial = reader.readLine()
        assertEquals("No subscribers connected", initial)
    }

    @Test
    fun whenPublisherSendsMessage_thenSubscriberReceive() = runBlocking {
        val subscriber = Socket("localhost", config.subscriberPort)
        openSockets += subscriber
        val subReader = BufferedReader(InputStreamReader(subscriber.getInputStream()))
        delay(100)

        val socket = Socket("localhost", config.publisherPort)
        openSockets += socket
        val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        reader.readLine()
        writer.write("hello\n")
        writer.flush()

        val received = subReader.readLine()
        assertEquals("hello", received)
    }

    @Test
    fun whenPublisherSendsMessage_thenAllSubscribersReceive() = runBlocking {
        val subscribers = List(3) {
            Socket("localhost", config.subscriberPort).also { openSockets += it }
        }
        val readers = subscribers.map { BufferedReader(InputStreamReader(it.getInputStream())) }

        delay(100)

        val socket = Socket("localhost", config.publisherPort)
        openSockets += socket
        val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        reader.readLine()
        writer.write("hello\n")
        writer.flush()

        val receivedMessages = readers.map { it.readLine() }
        receivedMessages.forEach { received ->
            assertEquals("hello", received)
        }
    }

    @Test
    fun whenSubscriberReceivesMultipleMessages_thenOrderIsPreserved() = runBlocking {
        val subscriber = Socket("localhost", config.subscriberPort)
        openSockets += subscriber
        val subReader = BufferedReader(InputStreamReader(subscriber.getInputStream()))
        delay(100)

        val socket = Socket("localhost", config.publisherPort)
        openSockets += socket
        val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        reader.readLine()
        writer.write("msg1\nmsg2\nmsg3\n")
        writer.flush()

        val msg1 = subReader.readLine()
        val msg2 = subReader.readLine()
        val msg3 = subReader.readLine()
        assertEquals("msg1", msg1)
        assertEquals("msg2", msg2)
        assertEquals("msg3", msg3)
    }

    @Test
    fun whenPublisherDisconnects_thenNoErrorThrown() = runBlocking {
        val socket = Socket("localhost", config.publisherPort)
        openSockets += socket
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        reader.readLine()
        socket.close()
        delay(100)
        assertTrue(logger.events.any { it is ServerEvent.PublisherDisconnected })
    }

    @Test
    fun invalidDataFromClient_thenHandledGracefully() = runBlocking {
        val socket = Socket("localhost", config.publisherPort)
        openSockets += socket
        val output = socket.getOutputStream()
        output.write(0x00)
        output.flush()
        socket.close()
        delay(100)
        assertTrue(logger.events.any { it is ServerEvent.Error })
    }

    @Test
    fun whenSubscriberDisconnects_thenPublishersListIsRenewed() = runBlocking {
        val subscriber = Socket("localhost", config.subscriberPort)
        openSockets += subscriber
        delay(100)
        subscriber.close()

        waitUntil {
            logger.events.any { it is ServerEvent.SubscriberDisconnected }
        }

        val socket = Socket("localhost", config.publisherPort)
        openSockets += socket
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        val initial = reader.readLine()
        assertEquals("No subscribers connected", initial)
    }

    @Test
    fun simultaneousPublisherConnections_thenNoDeadlockOrRaceCondition() = runBlocking {
        val subscriber = Socket("localhost", config.subscriberPort)
        openSockets += subscriber
        val subReader = BufferedReader(InputStreamReader(subscriber.getInputStream()))
        delay(100)

        coroutineScope {
            repeat(5) { i ->
                launch {
                    val pub = Socket("localhost", config.publisherPort)
                    openSockets += pub
                    val writer = BufferedWriter(OutputStreamWriter(pub.getOutputStream()))
                    val reader = BufferedReader(InputStreamReader(pub.getInputStream()))
                    reader.readLine()
                    writer.write("msg-from-$i\n")
                    writer.flush()
                }
            }
        }

        val receivedMessages = mutableListOf<String>()
        repeat(5) {
            receivedMessages += subReader.readLine()
        }

        assertTrue(receivedMessages.all { it.startsWith("msg-from-") })
    }

    suspend fun waitUntil(
        timeoutMs: Long = 2000,
        stepMs: Long = 50,
        condition: () -> Boolean
    ) {
        val attempts = (timeoutMs / stepMs).toInt()
        repeat(attempts) { i ->
            if (condition()) return
            delay(stepMs)
        }
        println("Condition failed after ${timeoutMs}ms: events=${logger.events}")
        error("Condition was not met within $timeoutMs ms")
    }
}