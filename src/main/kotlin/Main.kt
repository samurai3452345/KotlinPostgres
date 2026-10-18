package com.java_dragons

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    val port = System.getenv("PORT")?.let {
        requireNotNull(it.toIntOrNull()?.takeIf { value -> value in 1..65535 }) {
            "PORT must be an integer between 1 and 65535"
        }
    } ?: 8080
    embeddedServer(Netty, host = "0.0.0.0", port = port) { module() }.start(wait = true)
}
