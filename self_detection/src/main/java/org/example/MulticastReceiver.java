package org.example;

import java.io.IOException;
import java.net.*;
import java.util.HashMap;

public class MulticastReceiver {
    private MulticastSocket socket;
    private final static int BUFFER_SIZE = 100;

    public MulticastReceiver(InetAddress localNetAddress, int myPort) {
        try {
            socket = new MulticastSocket(myPort);
            // NetworkInterface networkInterface = getMulticastInterface();
            NetworkInterface networkInterface = NetworkInterface.getByInetAddress(InetAddress.getByName("127.0.0.1"));
            socket.setNetworkInterface(networkInterface);
            socket.joinGroup(new InetSocketAddress(localNetAddress, myPort), networkInterface);
        } catch (IOException e) {
            System.out.println("can't open socket in receiver");
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
                if (message.equals("I'm alive!")) {
                    InetAddress sourceAddress = packet.getAddress();
                    int sourcePort = packet.getPort();
                    String keyOfCopy = new String(sourceAddress.toString() + ":" + sourcePort);
                    synchronized (mutex) {
                        copies.put(keyOfCopy, System.currentTimeMillis());
                    }
                }
                packet.setLength(buffer.length);
            } catch (IOException e) {
                System.out.println("problem in receiving");
                throw new RuntimeException(e);
            }
        }

    }
}
