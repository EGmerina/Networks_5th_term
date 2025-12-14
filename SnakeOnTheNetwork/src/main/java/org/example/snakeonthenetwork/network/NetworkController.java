package org.example.snakeonthenetwork.network;

import me.ippolitov.fit.snakes.SnakesProto;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.snakeonthenetwork.controller.MainController;
import org.example.snakeonthenetwork.ui.MenuController;

import java.io.IOException;
import java.net.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;

public class NetworkController {

    private static final Logger logger = LogManager.getLogger(NetworkController.class);
    private static int stateDelay = 0;
    private static final int MULTICAST_PORT = 9192;
    private static final String MULTICAST_GROUP = "239.192.0.4";
    private static int RETRANSMIT_TIMEOUT_MS = stateDelay / 10; // Интервал переотправки (state_delay / 10)


    private final MainController controller;

    private DatagramSocket unicastSocket;
    private MulticastSocket multicastSocket;

    private Thread unicastThread;    // Бесконечный цикл приема UDP
    private Thread multicastThread;  // Бесконечный цикл приема Multicast
    private ScheduledExecutorService scheduler; // Таймер для переотправки (Reliability)

    private final AtomicLong seqCounter = new AtomicLong(0);
    private final Map<Long, PendingMessage> pendingMessages = new ConcurrentHashMap<>();
    private final Map<Integer, Long> receivedMessages = new ConcurrentHashMap<>();


    public NetworkController(MainController mainController) {
        this.controller = mainController;
    }

    public void start() {
        try {
            unicastSocket = new DatagramSocket();
            unicastThread = new Thread(this::listenUnicast, "UnicastListener");
            unicastThread.start();

            multicastSocket = new MulticastSocket(MULTICAST_PORT);
            InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
            multicastSocket.joinGroup(group); //TODO поменять функцию
            multicastThread = new Thread(this:: listenMulticast, "MulticastListener");
            multicastThread.start();

        } catch (SocketException e) {
            logger.error("can't open datagram socket");
            throw new RuntimeException(e);
        } catch (IOException e) {
            logger.error("can't open multicast socket");
            throw new RuntimeException(e);
        }
    }

    private void listenMulticast() {
        while (true){

        }
    }

    private void listenUnicast() {
    }

    public void stop() {
    }

    public void sendSteer(SnakesProto.Direction dir, int masterId) {
    }

    public void broadcastState(SnakesProto.GameState nextState) {
    }

    public void sendAnnouncement(SnakesProto.GameState nextState, SnakesProto.GameConfig gameConfig, String senderIp, int senderPort) {
    }

    public void sendJoin(String gameName) {
    }

    public void sendAck(int senderId) {
    }

    public void onAckReceived(long msgSeq) {
        // Удаляем сообщение из списка ожидания.
        // Таймер переотправки больше не будет его слать.
        PendingMessage removed = pendingMessages.remove(seq);
        if (removed != null) {
            System.out.println("Message " + seq + " acknowledged.");
        }
    }
}
