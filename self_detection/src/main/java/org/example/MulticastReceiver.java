package org.example;

import java.io.IOException;
import java.net.InetAddress;
import java.net.MulticastSocket;

public class MulticastReceiver {
    private MulticastSocket socket;

    public MulticastReceiver(InetAddress localNetAddress, int myPort) {
        try {
            socket = new MulticastSocket(myPort);

        } catch (IOException e) {
            System.out.println("can't open socket i receiver");
            throw new RuntimeException(e);
        }
    }

    public void startReceiving() {
    }
}
