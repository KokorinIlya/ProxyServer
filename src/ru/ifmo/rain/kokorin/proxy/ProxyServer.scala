package ru.ifmo.rain.kokorin.proxy

import java.io.IOException
import java.net.{InetAddress, InetSocketAddress}
import java.nio.channels._
import java.nio.file.{Files, Paths}
import java.util.concurrent.Executors

import ru.ifmo.rain.kokorin.utils.withResources

import scala.collection.concurrent.TrieMap
import scala.collection.immutable.HashSet
import scala.collection.Set

class ProxyServer (threads: Int) extends AutoCloseable {

    private val awaitAcceptSelector = Selector.open()
    private var isOpened = false

    private var listeners: Set[SelectableChannel] = new HashSet[SelectableChannel]
    private var sockets: Set[SocketChannel] = new TrieMap[SocketChannel, Unit].keySet

    private val threadPool = Executors.newFixedThreadPool(threads)


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

        println(s"Created listener: $curChannel")

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
                    println(s"New client connection: $socketChannel")
                    sockets += socketChannel

                    //TODO

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

    private def closeAll[T <: AutoCloseable](s: Set[T]): Option[IOException] = {
        var e: IOException = null

        for {
            channel <- s
        } {
            try {
                println(s"Closing channel $channel")
                channel.close()
            } catch {
                case ee: IOException => if (e == null) {
                    e = ee
                } else {
                    e.addSuppressed(ee)
                }
            }
        }

        Option(e)
    }

    /*
    Close method will be invoked, if error in proxy server occurs

    All resources will be closed
     */
    override def close(): Unit = {
        isOpened = false
        awaitAcceptSelector.close()

        closeAll(listeners) match {
            case None => closeAll(sockets) match {
                case None => {}
                case Some(e) => throw e
            }

            case Some(e) => closeAll(sockets) match {
                case None => throw e
                case Some(ee) => {
                    e.addSuppressed(ee)
                    throw e
                }
            }
        }
    }
}