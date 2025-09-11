package org.example;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

public class SelfDetector {
    private HashMap<String, Long> copies = new HashMap<>();
    private static InetAddress localNetAddress;
    private static final int TIME_OF_DEATH = 1000; //ms
    private Object mutex = new Object();
    private int myPort;

    SelfDetector(String localNetAddr, int port, HashMap<String, Long> copies) {
        try {
            localNetAddress = InetAddress.getByName(localNetAddr);

        } catch (UnknownHostException e) {
            System.out.println("unknown ip address");
            throw new RuntimeException(e);
        }
        this.copies = copies;
        myPort = port;
    }

    public void detect() {
        MulticastReceiver multicastReceiver = new MulticastReceiver(localNetAddress, myPort);
        MulticastSender multicastSender = new MulticastSender(localNetAddress, myPort);

        Thread senderThread = new Thread(() -> {
            multicastSender.startSending();
        });
        Thread receiverThread = new Thread(() -> {
            multicastReceiver.startReceiving(copies, mutex);
        });
        Thread checkerThread = new Thread(() -> {
            checkingTheLiving();
        });

        senderThread.start();
        receiverThread.start();
        checkerThread.start();
    }

    private void checkingTheLiving() {
        while (true) {
            synchronized (mutex) {
                for (Map.Entry<String, Long> copy : copies.entrySet()) {
                    if (System.currentTimeMillis() - copy.getValue() > TIME_OF_DEATH) {

                        copies.remove(copy.getKey());
                    }
                }
            }
        }
    }
}
