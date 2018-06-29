package ru.ifmo.rain.kokorin.simpleclient

import java.io.IOException
import java.net.{InetAddress, InetSocketAddress, Socket}
import java.nio.charset.StandardCharsets
import java.util.Scanner
import java.util.concurrent.ConcurrentLinkedQueue

object Client {
    def main(args: Array[String]): Unit = {

        if (args.length != 2) {
            println("Usage:\n" +
                "Client <address to connect> <port>")
            return
        }
        val socket = new Socket()
        socket.connect(
            new InetSocketAddress(
                InetAddress.getByName(args(0)),
                Integer.parseInt(args(1))
            )
        )

        new Thread(
            () => {
                val scanner = new Scanner(System.in)
                while (scanner.hasNext) {
                    val line = scanner.nextLine() + "\r\n"
                    socket.getOutputStream.write(line.getBytes(StandardCharsets.UTF_8))
                }
                socket.close()
            }
        ).start()

        var curTail = ""
        val buffer = new Array[Byte](2048)
        while (!socket.isInputShutdown) {
            val readBytes = try {
                socket.getInputStream.read(buffer)
            } catch {
                case e: IOException => return
            }

            val curArr = new Array[Byte](readBytes)
            System.arraycopy(buffer, 0, curArr, 0, readBytes)
            val line = new String(curArr, StandardCharsets.UTF_8)
            if (line.contains("\r\n")) {
                val pos = line.indexOf("\r\n")
                println("Answer: " + curTail + line.substring(0, pos))
                curTail = line.substring(pos + 2)
            } else {
                curTail += line
            }
        }

    }
}