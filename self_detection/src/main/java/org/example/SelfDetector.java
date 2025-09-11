package org.example;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class SelfDetector {
    private HashMap<InetAddress, Long> copies = new HashMap<>();
    private static InetAddress localNetAddress;
    private static final int TIME_OF_DEATH = 1000; //ms
    private Object mutex = new Object();

    SelfDetector(String localNetAddr, HashMap<InetAddress, Long> copies) {
        try {
            localNetAddress = InetAddress.getByName(localNetAddr);

        } catch (UnknownHostException e) {
            System.out.println("unknown ip address");
            throw new RuntimeException(e);
        }
        this.copies = copies;
    }

    public void detect() {
        MulticastReceiver multicastReceiver = new MulticastReceiver();
        MulticastSender multicastSender = new MulticastSender();

        Thread senderThread = new Thread(() -> {
            multicastSender.startSending();
        });
        Thread receiverThread = new Thread(() -> {
            multicastReceiver.startReceiving();
        });

        senderThread.start();
        receiverThread.start();

        while (true) {
            for (Map.Entry<InetAddress, Long> copy : copies.entrySet()) {
                if (System.currentTimeMillis() - copy.getValue() > TIME_OF_DEATH) {
                    synchronized (mutex) {
                        copies.remove(copy.getKey());
                    }
                }
            }
        }
    }
}
