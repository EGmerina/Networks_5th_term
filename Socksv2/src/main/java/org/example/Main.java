package org.example;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {

        if (args.length != 1) {
            System.err.println("Usage: java SocksProxy <port>");
            System.exit(1);
        }
        new SocksProxy(Integer.parseInt(args[0])).start();

    }
}
//java -jar target/SOCKS-proxy-1.0-SNAPSHOT.jar 8888