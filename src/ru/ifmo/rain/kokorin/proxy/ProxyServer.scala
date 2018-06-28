package ru.ifmo.rain.kokorin.proxy

import java.io.IOException
import java.net._
import java.nio.ByteBuffer
import java.nio.channels._
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.util
import java.util.concurrent.{Executors, TimeUnit}

import ru.ifmo.rain.kokorin.utils.withResources

import scala.collection.concurrent.TrieMap
import scala.collection.immutable.HashSet
import scala.collection.{Set, mutable}

class ProxyServer (threads: Int) extends AutoCloseable {

    private val awaitAcceptSelector = Selector.open()
    private var isOpened = false

    // Closable resources
    private var listeners: Set[SelectableChannel] = new HashSet[SelectableChannel]
    private var sockets: Set[SocketChannel] = new TrieMap[SocketChannel, Unit].keySet

    private val threadPool = Executors.newFixedThreadPool(threads)
    private val remoteAddressMap = mutable.Map[Int, SocketAddress]()

    private def processLine(line: String): Unit = {
        val parts = line.split(" ")

        if (parts.length != 3) {
            throw new IllegalArgumentException(
                "Incorrect file format: " + line
            )
        }

        val (localPort, remoteHost, remotePort) = (
            Integer.parseInt(parts(0)),
            parts(1),
            Integer.parseInt(parts(2))
        )

        remoteAddressMap += (
            localPort -> new InetSocketAddress(
                InetAddress.getByName(remoteHost),
                remotePort
            )
        )

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

    private def processConnection(clientChannel: SocketChannel): Unit = {

        def closeClientConnection(): Unit = {
            sockets -= clientChannel
            clientChannel.close()
        }

        val localPort = clientChannel.socket().getLocalPort
        val remoteAddress = remoteAddressMap(localPort)

        def getSize(s: Socket) = Math.max(s.getReceiveBufferSize, s.getSendBufferSize)

        def processImpl(): Unit = withResources(Selector.open()) {
            selector => {
                withResources(SocketChannel.open()) {
                    remoteChannel => {

                        try {
                            remoteChannel.socket().connect(remoteAddress, 1)
                        } catch {
                            case e: ConnectException => {
                                closeClientConnection()
                                return
                            }
                        }

                        var clientCLosedConnection = false
                        var remoteCLosedConection = false

                        remoteChannel.configureBlocking(false)
                        clientChannel.configureBlocking(false)

                        val (remoteBuffer, clientBuffer) = (
                            ByteBuffer.allocate(getSize(remoteChannel.socket())),
                            ByteBuffer.allocate(getSize(clientChannel.socket()))
                        )

                        // Queue for sending TO client and TO remote server
                        val clientDeque = new util.ArrayDeque[Array[Byte]]()
                        val remoteDeque = new util.ArrayDeque[Array[Byte]]()

                        remoteChannel.register(selector, SelectionKey.OP_READ)
                        clientChannel.register(selector, SelectionKey.OP_READ)

                        while (true) {
                            selector.select()
                            val iter = selector.selectedKeys().iterator()

                            while (iter.hasNext) {
                                val key = iter.next()

                                if (key.channel() == remoteChannel) {
                                    remoteCLosedConection |= processEvent(
                                        remoteChannel,
                                        remoteDeque,
                                        remoteBuffer,
                                        clientChannel,
                                        clientDeque,
                                        clientBuffer,
                                        key,
                                        selector
                                    )
                                } else {
                                    clientCLosedConnection |= processEvent(
                                        clientChannel,
                                        clientDeque,
                                        clientBuffer,
                                        remoteChannel,
                                        remoteDeque,
                                        remoteBuffer,
                                        key,
                                        selector
                                    )
                                }
                                iter.remove()
                            }

                            if (clientCLosedConnection && remoteCLosedConection) {
                                closeClientConnection()
                                return
                            }
                        }
                    }
                }
            }
        }

        processImpl()
        println(s"Finished connection for client $clientChannel")

    }

    private def processEvent(
        channel: SocketChannel,
        deque: util.ArrayDeque[Array[Byte]],
        buffer: ByteBuffer,
        otherChannel: SocketChannel,
        otherDeque: util.ArrayDeque[Array[Byte]],
        otherBuffer: ByteBuffer,
        key: SelectionKey,
        selector: Selector
    ): Boolean = {
        if (key.isReadable) {
            buffer.clear()
            if (channel.read(buffer) == -1) {
                return true
            }
            buffer.flip()
            val arr = new Array[Byte](buffer.remaining())
            buffer.get(arr)
            otherDeque.addFirst(arr)
            buffer.clear
            if (otherDeque.size == 1) {
                otherChannel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE)
            }
        }
        if (key.isWritable) {
            buffer.clear()

            buffer.put(deque.removeLast())
            buffer.flip()
            if (channel.write(buffer) == -1) {
                return true
            }
            if (buffer.remaining > 0) {
                val arr = new Array[Byte](buffer.remaining())
                buffer.get(arr)
                deque.addLast(arr)
            }
            if (deque.isEmpty) {
                channel.register(selector, SelectionKey.OP_READ)
            }
            buffer.clear()
        }
        return false
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

            val iter = awaitAcceptSelector.selectedKeys().iterator()
            while (iter.hasNext) {
                val key = iter.next()
                require(key.isAcceptable)

                val serverChannel = key.channel().asInstanceOf[ServerSocketChannel]

                try {
                    val socketChannel = serverChannel.accept()
                    println(s"New client connection: $socketChannel")
                    sockets += socketChannel

                    val task: Runnable = () => processConnection(socketChannel)
                    threadPool.submit(task)

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

        threadPool.shutdownNow()
        threadPool.awaitTermination(1, TimeUnit.MINUTES)

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