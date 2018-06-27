package ru.ifmo.rain.kokorin.simpleserver

import java.net.{InetAddress, InetSocketAddress, ServerSocket}

object Server {
    def main(args: Array[String]): Unit = {
        val serverSocket= new ServerSocket()
        serverSocket.bind(
            new InetSocketAddress(
                InetAddress.getLocalHost,
                2337
            )
        )

        val socket = serverSocket.accept()
        println(s"Connected $socket")

        val x = socket.getInputStream.read()
        println(x)
        socket.close()
        serverSocket.close()
    }
}
