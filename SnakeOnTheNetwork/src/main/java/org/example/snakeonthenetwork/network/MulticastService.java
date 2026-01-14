package org.example.snakeonthenetwork.network;

import me.ippolitov.fit.snakes.SnakesProto;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class MulticastService {

    private static final Logger logger = LogManager.getLogger(MulticastService.class);

    private static final String MULTICAST_GROUP_IP = "239.192.0.4";
    private static final int MULTICAST_PORT = 9192;
    private static final int BUFFER_SIZE = 65535;

    private final NetworkController networkController;

    // ЕДИНСТВЕННЫЙ сокет
    private MulticastSocket socket;

    private InetSocketAddress groupAddress;
    private final ExecutorService listenExecutor = Executors.newSingleThreadExecutor();
    private Future<?> listenTask;

    public MulticastService(NetworkController networkController) {
        this.networkController = networkController;
        try {

            this.socket = new MulticastSocket(MULTICAST_PORT);

            NetworkInterface netIf = chooseNetworkInterface();
            logger.info("Multicast service using interface: " + netIf.getName());

            this.socket.setNetworkInterface(netIf);

            this.socket.setOption(StandardSocketOptions.IP_MULTICAST_LOOP, false);

            // 5. Джойнимся к группе
            this.groupAddress = new InetSocketAddress(MULTICAST_GROUP_IP, MULTICAST_PORT);
            this.socket.joinGroup(groupAddress, netIf);

            logger.info("Joined multicast group " + groupAddress);

        } catch (IOException e) {
            logger.error("Failed to create MulticastSocket", e);
            throw new RuntimeException(e);
        }
    }

    public void start() {
        listenTask = listenExecutor.submit(() -> {
            try {
                logger.info("Multicast receiver thread started.");
                byte[] buffer = new byte[BUFFER_SIZE];

                while (!Thread.currentThread().isInterrupted()) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                    try {

                        socket.receive(packet);

                        byte[] data = Arrays.copyOf(packet.getData(), packet.getLength());
                        SnakesProto.GameMessage message = SnakesProto.GameMessage.parseFrom(data);

                        logger.debug("Received multicast from {}:{} type={}",
                                packet.getAddress(), packet.getPort(), message.getTypeCase());

                        networkController.handleMessage(message, packet.getAddress(), packet.getPort());

                    } catch (SocketException e) {
                        logger.info("Multicast socket closed, stopping receiver thread.");
                        break;
                    } catch (IOException e) {
                        logger.error("IO Error in receive loop", e);
                        if (socket.isClosed()) break;
                    } catch (Exception e) {
                        logger.error("CRITICAL ERROR handling message from " + packet.getAddress(), e);
                    }
                }

            } catch (Exception e) {
                logger.error("Multicast receiver thread died unexpectedly", e);
            }
        });
    }

    public void stop() {
        if (listenTask != null) {
            listenTask.cancel(true);
        }

        if (socket != null && !socket.isClosed()) {
            try {
                socket.leaveGroup(groupAddress, socket.getNetworkInterface());
            } catch (IOException e) {
                logger.error("Error leaving group", e);
            }
            socket.close();
        }
        listenExecutor.shutdownNow();
    }

    public void send(SnakesProto.GameMessage msg) {
        try {
            byte[] data = msg.toByteArray();
            // Отправляем через ТОТ ЖЕ сокет
            DatagramPacket packet = new DatagramPacket(data, data.length, groupAddress);
            socket.send(packet);

            logger.trace("Sent multicast message: " + msg.getTypeCase());
        } catch (IOException e) {
            logger.error("Failed to send multicast message", e);
        }
    }

    private NetworkInterface chooseNetworkInterface() throws SocketException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface netIf = interfaces.nextElement();
            if (!netIf.isUp()) continue;

            // Оставляем Loopback, пока тестируете на одном ПК
            if (netIf.isLoopback()) {
                return netIf;
            }
        }
        return NetworkInterface.getNetworkInterfaces().nextElement();
    }
}