package org.example.snakeonthenetwork.network;

import me.ippolitov.fit.snakes.SnakesProto;
import org.example.snakeonthenetwork.controller.MainController;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;

public class NetworkController {

    private static int stateDelay = 0;
    private static final int MULTICAST_PORT = 9192;
    private static final String MULTICAST_GROUP = "239.192.0.4";
    private static int RETRANSMIT_TIMEOUT_MS = stateDelay / 10; // Интервал переотправки (state_delay / 10)


    private final MainController controller;

    private DatagramSocket unicastSocket;
    private MulticastSocket multicastSocket;

    private Thread unicastReceiverThread;    // Бесконечный цикл приема UDP
    private Thread multicastReceiverThread;  // Бесконечный цикл приема Multicast
    private ScheduledExecutorService scheduler; // Таймер для переотправки (Reliability)

    private final AtomicLong seqCounter = new AtomicLong(0);

    // "Зал ожидания" для сообщений, на которые мы ждем ACK
    // Ключ: msg_seq, Значение: Контекст сообщения (что, куда, когда отправили)
    //private final ConcurrentHashMap<Long, PendingMessage> sentMessages = new ConcurrentHashMap<>();

    // Храним последний обработанный msg_seq от каждого игрока (Map<PlayerID, LastSeq>)
    // Чтобы не обрабатывать одно и то же сообщение дважды, если ACK потерялся
    private final Map<Integer, Long> receivedMessages = new ConcurrentHashMap<>();


    public NetworkController(MainController mainController) {
        this.controller = mainController;
    }

    public void start() {
    }

    public void stop() {
    }

    public void sendSteer(SnakesProto.Direction dir, int masterId) {
    }

    public void broadcastState(SnakesProto.GameState nextState) {
    }

    public void sendAnnouncement(SnakesProto.GameState nextState, SnakesProto.GameConfig gameConfig) {
    }

    public void sendJoin(String gameName, InetAddress host, int port, SnakesProto.NodeRole myRole) {
    }
}
