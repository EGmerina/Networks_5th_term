package org.example;

import java.util.HashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Main {
    private static HashMap<String, Long> copies = new HashMap<>();

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage : java Main <ip_local_network> <my_port>");
        }

        MyInterface userInterface = new MyInterface();
        String localNetAddr = args[0];
        int port = Integer.parseInt(args[1]);
        SelfDetector selfDetector = new SelfDetector(localNetAddr, port, copies);
        selfDetector.detect();
        userInterface.show(copies);
    }
}