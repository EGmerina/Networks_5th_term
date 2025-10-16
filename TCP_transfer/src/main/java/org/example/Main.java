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


//java -jar target/TCP_transfer-1.0-SNAPSHOT-jar-with-dependencies.jar 8888
// java -jar target/TCP_transfer-1.0-SNAPSHOT-jar-with-dependencies.jar pom.xml 127.0.0.1 8888
// java -jar target/TCP_transfer-1.0-SNAPSHOT-jar-with-dependencies.jar ~/Documents/CA-NEW.pdf 127.0.0.1 8888