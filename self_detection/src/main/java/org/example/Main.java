package org.example;

import java.net.InetAddress;
import java.util.HashMap;

public class Main {
    private static HashMap<InetAddress, Long> copies = new HashMap<>();

    public static void main(String[] args) {
        if(args.length != 2){
            System.out.println("Usage : java Main <ip_local_network>");
        }

        String localNetAddr = args[2];
        SelfDetector selfDetector = new SelfDetector(localNetAddr, copies);
        selfDetector.detect();
    }
}