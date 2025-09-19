package org.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class SelfDetector {
    private static final Logger logger = LogManager.getLogger(SelfDetector.class);
    private HashMap<String, Long> copies = new HashMap<>();
    private static InetAddress localNetAddress;
    private static final int TIME_OF_DEATH = 1000; //ms
    private final Object mutex;
    private int myPort;

    SelfDetector(String localNetAddr, int port, HashMap<String, Long> copies, Object mutex) {
        try {
            localNetAddress = InetAddress.getByName(localNetAddr);
        } catch (UnknownHostException e) {
            logger.error("unknown ip address");
            throw new RuntimeException(e);
        }
        this.copies = copies;
        myPort = port;
        this.mutex = mutex;
    }

    public void detect() {
        logger.trace("start detecting");
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
                Iterator<Map.Entry<String, Long>> iterator = copies.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<String, Long> copy = iterator.next();
                    if (System.currentTimeMillis() - copy.getValue() > TIME_OF_DEATH) {
                        iterator.remove();
                    }
                }
            }

        }
    }
}
