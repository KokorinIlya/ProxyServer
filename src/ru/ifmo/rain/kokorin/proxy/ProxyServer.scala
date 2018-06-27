package ru.ifmo.rain.kokorin.proxy

import java.io.IOException
import java.net.{InetAddress, InetSocketAddress}
import java.nio.channels.{SelectionKey, Selector, ServerSocketChannel, SocketChannel}
import java.nio.file.{Files, Path, Paths}

import ru.ifmo.rain.kokorin.utils.withResources

class ProxyServer extends AutoCloseable {

    private val awaitAcceptSelector = Selector.open()
    private var isOpened = false

    private def processLine(line: String): Unit = {
        val parts = line.split(" ")

        if (parts.length != 3) {
            throw new IllegalArgumentException(
                "Incorrect file format: " + line
            )
        }

        ServerSocketChannel.open().bind(
            new InetSocketAddress(
                InetAddress.getLocalHost,
                Integer.parseInt(parts(0))
            )
        ).configureBlocking(false).register(
            awaitAcceptSelector,
            SelectionKey.OP_ACCEPT
        )

    }

    def start(fileName: String): Unit = {

        isOpened = true

        val pathToConfigFile = Paths.get(fileName)

        withResources(Files.newBufferedReader(pathToConfigFile)) {
            reader => reader.lines().forEach {
                processLine(_)
            }
        }

        while (isOpened && !Thread.interrupted()) {
            awaitAcceptSelector.select()

            println("Selection completed")

            val iter = awaitAcceptSelector.selectedKeys().iterator()
            while (iter.hasNext) {
                val key = iter.next()
                require(key.isAcceptable)

                val serverChannel = key.channel().asInstanceOf[ServerSocketChannel]

                try {
                    val socketChannel = serverChannel.accept()

                    //TODO
                    println(socketChannel)
                    socketChannel.close()
                } catch {
                    case e: IOException => System.err.println(
                        s"Acception error on channel $serverChannel" +
                            s" has occurred; ${e.getMessage}"
                    )
                }

                iter.remove()
            }
        }

    }

    override def close(): Unit = {
        isOpened = false
        awaitAcceptSelector.close()
    }
}