package ru.ifmo.rain.kokorin.proxy

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
                    Thread.sleep(10000)
                    server.close()
                    println("Closed")
                }
            ).start()

            server.start(args(0))

        } catch {
            case e: Exception => println(e.getMessage)
        }

    }
}
