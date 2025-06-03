package lt.drelis

import java.util.Properties

data class ServerConfig(
    val publisherPort: Int,
    val subscriberPort: Int,
    val noSubscribersMessage: String,
    val subscriberCountMessage: String
)

object ConfigHolder {
    private var config: ServerConfig? = null

    fun get(): ServerConfig {
        if (config == null) {
            config = loadFromFile()
        }
        return config!!
    }

    fun set(newConfig: ServerConfig) {
        config = newConfig
    }

    private fun loadFromFile(path: String = "application.properties"): ServerConfig {
        val props = Properties()
        val inputStream = ConfigHolder::class.java.classLoader.getResourceAsStream(path)
            ?: error("Could not find $path in classpath")

        props.load(inputStream)

        return ServerConfig(
            publisherPort = props.getProperty("publisherPort")?.toInt()
                ?: error("Missing 'publisherPort'"),
            subscriberPort = props.getProperty("subscriberPort")?.toInt()
                ?: error("Missing 'subscriberPort'"),
            noSubscribersMessage = props.getProperty("noSubscribersMessage")
                ?: "No subscribers connected",
            subscriberCountMessage = props.getProperty("subscriberCountMessage")
                ?: "No subscribers connected",
        )
    }
}