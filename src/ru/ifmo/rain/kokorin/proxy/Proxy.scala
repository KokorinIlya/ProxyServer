package ru.ifmo.rain.kokorin.proxy

import java.util.Scanner

object Proxy {
    def main(args: Array[String]): Unit = {

        if (args.length != 2) {
            println(
                "running:\n" +
                "Proxy <config file> <number of threads>\n" +
                "file format:\n" +
                "<local port> <remote ip-address> <remote port>"
            )
            return
        }

        try {
            val server = new ProxyServer(Integer.parseInt(args(1)))

            new Thread(
                () => {
                    println("Type 'close' for closing")
                    val scanner = new Scanner(System.in)
                    while (scanner.next() != "close") {}
                    server.close()
                }
            ).start()

            server.start(args(0))

        } catch {
            case e: Exception => println(e.getMessage)
        }

    }
}
