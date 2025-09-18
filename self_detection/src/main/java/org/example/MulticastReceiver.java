package org.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.*;
import java.util.HashMap;

public class MulticastReceiver {
    private MulticastSocket socket;
    private final static int BUFFER_SIZE = 100;
    private static final Logger logger = LogManager.getLogger(MulticastReceiver.class);

    public MulticastReceiver(InetAddress localNetAddress, int myPort) {
        try {
            socket = new MulticastSocket(myPort);
            socket.joinGroup(localNetAddress);
            logger.trace("inet socket address :" + localNetAddress + " " + myPort + " joined the group ");
        } catch (IOException e) {
            logger.error("can't open socket in receiver");
            throw new RuntimeException(e);
        }
    }

    public void startReceiving(HashMap<String, Long> copies, Object mutex) {
        byte[] buffer = new byte[BUFFER_SIZE];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        while (true) {
            try {
                socket.receive(packet);
                String message = new String(packet.getData(), 0, packet.getLength(), "UTF-8");
                logger.trace("receive message " + message);
                if (message.equals("I'm alive!")) {
                    InetAddress sourceAddress = packet.getAddress();
                    int sourcePort = packet.getPort();
                    logger.trace(sourceAddress.toString() + ":" + sourcePort + " is alive");
                    String keyOfCopy = new String(sourceAddress.toString() + ":" + sourcePort);
                    synchronized (mutex) {
                        copies.put(keyOfCopy, System.currentTimeMillis());
                    }
                }
                packet.setLength(buffer.length);
            } catch (IOException e) {
                logger.warn("problem in receiving");
            }
        }

    }
}
