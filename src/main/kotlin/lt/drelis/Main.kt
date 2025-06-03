package lt.drelis

fun main() {
    val config = ConfigHolder.get()
    val eventLogger = Log4jEventLogger()
    val server = TcpPubSubServer(config, eventLogger)
    server.start()
    Thread.currentThread().join()
}
