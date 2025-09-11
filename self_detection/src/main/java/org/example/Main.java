package org.example;

import java.util.HashMap;

public class Main {
    private static HashMap<String, Long> copies = new HashMap<>();

    public static void main(String[] args) {
        if (args.length > 3) {
            System.out.println("Usage : java Main <ip_local_network> <my_port>");
        }

        MyInterface userInterface = new MyInterface();
        String localNetAddr = args[2];
        int port = Integer.parseInt(args[3]);
        SelfDetector selfDetector = new SelfDetector(localNetAddr, port, copies);
        selfDetector.detect();
        userInterface.show(copies);
    }
}