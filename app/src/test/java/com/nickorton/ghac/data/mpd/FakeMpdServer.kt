package com.nickorton.ghac.data.mpd

import java.io.Closeable
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import kotlin.concurrent.thread

/**
 * A scripted MPD server on a loopback port, standing in for a real daemon.
 *
 * This is the Kotlin counterpart of the Go project's build-tagged integration
 * tests: it exercises the real socket, framing and threading paths without
 * needing MPD installed on the machine running the tests.
 *
 * [handler] receives each command line and returns the reply's `key: value`
 * lines. The trailing `OK` is appended automatically unless the handler itself
 * returns an `ACK` line.
 */
class FakeMpdServer(
    private val greeting: String = "OK MPD 0.23.5",
    private val handler: (String) -> List<String>,
) : Closeable {

    private val server = ServerSocket(0)

    val port: Int get() = server.localPort

    private val _received = Collections.synchronizedList(mutableListOf<String>())

    /** Every command line received, across all connections, in arrival order. */
    val received: List<String> get() = synchronized(_received) { _received.toList() }

    init {
        thread(isDaemon = true, name = "fake-mpd-acceptor") {
            runCatching {
                while (!server.isClosed) {
                    val socket = server.accept()
                    thread(isDaemon = true, name = "fake-mpd-conn") { serve(socket) }
                }
            }
        }
    }

    private fun serve(socket: Socket) {
        runCatching {
            socket.use {
                val reader = it.getInputStream().bufferedReader()
                val writer = it.getOutputStream().bufferedWriter()

                writer.write(greeting)
                writer.write("\n")
                writer.flush()

                while (true) {
                    val line = reader.readLine() ?: return
                    _received += line

                    val reply = handler(line)
                    reply.forEach { replyLine ->
                        writer.write(replyLine)
                        writer.write("\n")
                    }
                    if (reply.none { replyLine -> replyLine.startsWith("ACK") }) {
                        writer.write("OK\n")
                    }
                    writer.flush()
                }
            }
        }
    }

    override fun close() {
        runCatching { server.close() }
    }
}
