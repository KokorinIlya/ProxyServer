package ru.ifmo.rain.kokorin.proxy

import java.io.IOException
import java.net.{InetAddress, InetSocketAddress}
import java.nio.channels._
import java.nio.file.{Files, Path, Paths}

import ru.ifmo.rain.kokorin.utils.withResources

import scala.collection.mutable

class ProxyServer extends AutoCloseable {

    private val awaitAcceptSelector = Selector.open()
    private var isOpened = false
    private val listeners = new mutable.HashSet[SelectableChannel]()

    private def processLine(line: String): Unit = {
        val parts = line.split(" ")

        if (parts.length != 3) {
            throw new IllegalArgumentException(
                "Incorrect file format: " + line
            )
        }

        val curChannel = ServerSocketChannel.open().bind(
            new InetSocketAddress(
                InetAddress.getLocalHost,
                Integer.parseInt(parts(0))
            )
        ).configureBlocking(false)

        listeners += curChannel

        curChannel.register(
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

            if (!isOpened || Thread.interrupted()) {
                return
            }

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
                        s"Accept error on channel $serverChannel" +
                            s" has occurred; ${e.getMessage}"
                    )
                }

                iter.remove()
            }
        }

    }

    /*
    Close method will be invoked, if error in proxy server occurs

    All resources will be closed
     */
    override def close(): Unit = {
        isOpened = false

        var e: IOException = null

        for {
            listener <- listeners
        } {
            try {
                listener.close()
            } catch {
                case ee: IOException => if (e == null) {
                    e = ee
                } else {
                    e.addSuppressed(ee)
                }
            }
        }

        awaitAcceptSelector.close()

        if (e != null) {
            throw e
        }
    }
}