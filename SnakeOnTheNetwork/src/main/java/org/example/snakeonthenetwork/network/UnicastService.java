package org.example.snakeonthenetwork.network;

import com.google.protobuf.InvalidProtocolBufferException;
import me.ippolitov.fit.snakes.SnakesProto;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class UnicastService {

    private static final Logger logger = LogManager.getLogger(UnicastService.class);

    private final DatagramSocket socket;
    private final NetworkController networkController;

    private final ExecutorService listenExecutor = Executors.newSingleThreadExecutor();
    private Future<?> listenTask;

    private static final int BUFFER_SIZE = 65535;

    public UnicastService(NetworkController networkController) {
        this.networkController = networkController;
        try {
            this.socket = new DatagramSocket();
            logger.info("Unicast socket started on port: " + socket.getLocalPort());
        } catch (SocketException e) {
            logger.error("Can't create datagram socket", e);
            throw new RuntimeException(e);
        }
    }

    public void start() {
        listenTask = listenExecutor.submit(() -> {
            logger.info("Unicast receiver thread started.");

            byte[] buffer = new byte[BUFFER_SIZE];

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                    socket.receive(packet);

                    byte[] data = Arrays.copyOf(packet.getData(), packet.getLength());

                    SnakesProto.GameMessage message = SnakesProto.GameMessage.parseFrom(data);

                    logger.debug("Received packet from {}:{} seq={}",
                            packet.getAddress(), packet.getPort(), message.getMsgSeq());


                    networkController.handleMessage(message, packet.getAddress(), packet.getPort());

                } catch (SocketException e) {
                    logger.info("Unicast socket closed, stopping receiver thread.");
                    break;
                } catch (IOException e) {
                    logger.error("Error receiving packet", e);
                } catch (Exception e) {
                    logger.error("Unknown error", e);
                }
            }
        });
    }

    public void stop() {
        if (listenTask != null) {
            listenTask.cancel(true);
        }

        if (socket != null && !socket.isClosed()) {
            socket.close();
        }

        listenExecutor.shutdownNow();
        logger.info("UnicastService stopped.");
    }


    public void send(SnakesProto.GameMessage msg, SocketAddress address) {
        try {
            byte[] data = msg.toByteArray();
            if (address == null) {
                logger.trace("ADDRESS IS NULL!!!!!!!!!!!!!!!!");
                return;
            }
            logger.trace("Sent message type {} to {}, id {}, seq = {}", msg.getTypeCase(), address, msg.getReceiverId(), msg.getMsgSeq());
            DatagramPacket packet = new DatagramPacket(data, data.length, address);
            socket.send(packet);

        } catch (IOException e) {
            logger.error("Failed to send message to " + address, e);
        }
    }


    public int getPort() {
        return socket.getLocalPort();
    }
}