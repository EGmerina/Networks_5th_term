package org.example;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        if (args.length == 1) {
            Server server = new Server();
            server.start(Integer.parseInt(args[0]));
        } else if (args.length == 3) {
            Path path = Paths.get(args[0]);
            InetSocketAddress inetSocketAddress = new InetSocketAddress(args[1], Integer.parseInt(args[2]));
            Client client = new Client();
            client.start(path, inetSocketAddress);
        } else {
            System.out.println("Usage: \n for Server : <port number>\n " +
                    "for Client : <file path> <server address> <server port>");
        }
    }
}