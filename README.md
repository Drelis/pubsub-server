# TCP Pub/Sub Message Broker

A simple TCP-based **publish/subscribe message broker** written in **Kotlin** using **coroutines**.  
It supports separate ports for **publishers** and **subscribers**, sends connection updates, and broadcasts messages to all active subscribers.

---

## Features

- Lightweight Kotlin implementation
- Coroutine-based concurrency using `Dispatchers.IO`
- TCP sockets for real-time communication
- Real socket-based integration tests (JUnit 5)
- Server-side event logging (in-memory or pluggable)
- Dynamic subscriber connection tracking
- Error handling and shutdown

---

## Configuration

Configuration is done via `ServerConfig`:

```kotlin
val config = ServerConfig(
    publisherPort = 9111,
    subscriberPort = 9222,
    noSubscribersMessage = "No subscribers connected",
    subscriberCountMessage = "{count} subscriber(s) connected"
)
```

application.properties:

publisherPort=8088
subscriberPort=8099

noSubscribersMessage=No subscribers connected
subscriberCountMessage={count} subscriber(s) connected
---

## How It Works

- **Publishers** connect to `publisherPort`. On connect:
  - They receive a message about the current number of subscribers.
  - They can send messages which are broadcast to all subscribers.
- **Subscribers** connect to `subscriberPort`. On connect:
  - They are added to the active subscriber list.
  - They receive all published messages in real-time.
- When a **subscriber disconnects**, all publishers are notified.
- If a **publisher** sends a message with no subscribers, a default message is returned.

---

## Running the Server

```kotlin
val server = TcpPubSubServer(config, Log4jEventLogger())
server.start()
```

Server listens on configured ports and handles clients concurrently.

---

## Testing

JUnit 5 tests using real TCP sockets are provided in `TcpPubSubServerTest.kt`.

Tested scenarios:
- Publisher receives proper subscriber count
- Subscribers receive all messages
- Message order is preserved
- Errors (like broken connections or invalid input) are logged
- Concurrency and simultaneous connections tested

To run tests:
```bash
./gradlew test
```

---

## Requirements

- Kotlin 1.9+
- JDK 17+
- Gradle
- No third-party networking libraries required

---

## Future Ideas

- Add WebSocket support
- Support custom message formats (e.g., JSON)
- Authentication and message topics
- Persisted event logger (file or DB)
- Graceful shutdown with lifecycle hooks

---

## Author

Created by [Drelis](https://github.com/Drelis)