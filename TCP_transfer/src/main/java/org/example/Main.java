package org.example;

public class Main {
    public static void main(String[] args) {
        if (args.length == 1) {
            Server server = new Server();
            server.start(Integer.parseInt(args[0]));
        } else if (args.length == 3) {
            Client client = new Client();
            client.start();
        } else {
            System.out.println("Usage: \n for Server : <port number>\n for Client : <file path> <server address> <server port>");
        }
    }
}