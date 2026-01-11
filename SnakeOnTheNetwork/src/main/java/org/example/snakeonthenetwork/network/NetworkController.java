package org.example.snakeonthenetwork.network;

import me.ippolitov.fit.snakes.SnakesProto;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.snakeonthenetwork.controller.MainController;

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

    //TODO хранить  map игроков и ip

    public NetworkController(MainController mainController) {
        this.controller = mainController;
    }

    public void start() {

    }

    private void listenMulticast() {
        while (true) {

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

    public void sendAnnouncement(SnakesProto.GameState nextState, SnakesProto.GameConfig gameConfig) {
    }

    public void sendAck(long msgSeq, int senderId) {
    }

    public void onAckReceived(long msgSeq) {
        // Удаляем сообщение из списка ожидания.
        // Таймер переотправки больше не будет его слать.
        PendingMessage removed = pendingMessages.remove(seq);
        if (removed != null) {
            System.out.println("Message " + seq + " acknowledged.");
        }
    }

    public void sendJoin(SnakesProto.GameMessage.JoinMsg join, InetAddress host, int port, long seq) {
        un
    }

    public long getNextSeq() {
    }

    public void sendError(String noSpaceOrGameFull, long msgSeq) {
    }

    public void startPingTask() {
    }

    public void sendChangeRole(int senderId, SnakesProto.NodeRole senderRole, int recId, SnakesProto.NodeRole recRole) {
    }

    public void sendChangeMaster(int masterId) {
    }

    public void sendPing() { //TODO сделать проверку на последнюю отправку время
    }
}
