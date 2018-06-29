package ru.ifmo.rain.kokorin.simpleserver

import java.net.{InetAddress, InetSocketAddress, ServerSocket}
import java.nio.charset.StandardCharsets

object Server {
    def main(args: Array[String]): Unit = {

        if (args.length != 1) {
            println("Usage:\n" +
                "Server <port to bind on>")
            return
        }

        val serverSocket= new ServerSocket()
        serverSocket.bind(
            new InetSocketAddress(
                InetAddress.getLocalHost,
                Integer.parseInt(args(0))
            )
        )

        val socket = serverSocket.accept()

        var curTail = ""
        val buffer = new Array[Byte](2048)

        while (!socket.isInputShutdown) {
            val bytesRead = socket.getInputStream.read(buffer)
            val curArr = new Array[Byte](bytesRead)
            System.arraycopy(buffer, 0, curArr, 0, bytesRead)
            val line = new String(curArr, StandardCharsets.UTF_8)
            println(s"Received: $line")
            if (line.contains("\r\n")) {
                val pos = line.indexOf("\r\n")
                val answer = s"Hello, ${line.substring(0, pos)}\r\n"
                println(s"Sending answer: $answer")
                socket.getOutputStream.write(answer.getBytes(StandardCharsets.UTF_8))
                curTail = line.substring(pos + 2)
            } else {
                curTail += line
            }
        }
    }
}
