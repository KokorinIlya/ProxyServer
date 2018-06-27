package ru.ifmo.rain.kokorin.simpleclient

import java.net.{InetAddress, InetSocketAddress, Socket}

object Client {
    def main(args: Array[String]): Unit = {
        val socket = new Socket()
        socket.connect(
            new InetSocketAddress(
                InetAddress.getLocalHost,
                1337
            )
        )
        Thread.sleep(1000)
        socket.close()
    }
}