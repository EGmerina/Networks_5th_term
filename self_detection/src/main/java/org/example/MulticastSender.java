package org.example;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.*;

public class MulticastSender {
    private MulticastSocket socket;
    private final static int BUFFER_SIZE = 100;
    private final static String healthCheck = "I'm alive!";
    private InetAddress localNetAddress;
    private int myPort;


    public MulticastSender(InetAddress localNetAddress, int myPort) {
        try {
            socket = new MulticastSocket(myPort);
            socket.setTimeToLive(0);
            this.localNetAddress = localNetAddress;
            this.myPort = myPort;
        } catch (IOException e) {
            System.out.println("can't open socket in receiver");
            throw new RuntimeException(e);
        }
    }

    public void startSending() {

        while (true) {
            try {
                byte[] buffer = healthCheck.getBytes("UTF-8");
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, localNetAddress, myPort);
                socket.send(packet);
                Thread.sleep(500);
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

    }
}
