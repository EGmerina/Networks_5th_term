package org.example.snakeonthenetwork.network;

import me.ippolitov.fit.snakes.SnakesProto;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.snakeonthenetwork.controller.MainController;

import java.net.*;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class NetworkController {

    private static final Logger logger = LogManager.getLogger(NetworkController.class);
    private static int stateDelay = 0;
    private static int RESEND_TIMEOUT_MS = stateDelay / 10;

    private final MainController controller;

    private MulticastService multicastService;
    private UnicastService unicastService;

    private ScheduledExecutorService resendTimer = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> resendTask = null;

    private final AtomicLong seqCounter = new AtomicLong(0);
    private final Map<Long, SnakesProto.GameMessage> unconfirmedMessages = new ConcurrentHashMap<>();
    private final Map<Integer, Long> receivedMessages = new ConcurrentHashMap<>(); //для проверки повторных пакетов
    private final Map<Integer, InetSocketAddress> playersAddresses = new ConcurrentHashMap<>();

    private AtomicLong lastSendTime = new AtomicLong(0);


    public NetworkController(MainController mainController) {
        this.controller = mainController;
        multicastService = new MulticastService(this);
        unicastService = new UnicastService(this);
    }

    public void start() {
        multicastService.start();
        unicastService.start();
        resendTask = resendTimer.scheduleAtFixedRate(() -> {
            unconfirmedMessages.forEach((seq, msg) -> {
                resend(msg);
            });
        }, 0, RESEND_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        multicastService.stop();
        unicastService.stop();
        unconfirmedMessages.clear();
        receivedMessages.clear();
        resendTask.cancel(true);
    }

    public void sendSteer(SnakesProto.Direction dir, int masterId) {
        long msg_seq = seqCounter.addAndGet(1);
        lastSendTime.set(System.currentTimeMillis());
        //TODO сформровать msg
        unconfirmedMessages.put(msg_seq, msg);
    }

    public void sendAck(long msgSeq, int senderId) {
        lastSendTime.set(System.currentTimeMillis());
    }


    public long sendJoin(SnakesProto.GameAnnouncement join) {
        long msg_seq = seqCounter.addAndGet(1);
        lastSendTime.set(System.currentTimeMillis());
        SnakesProto.GameMessage.JoinMsg join = SnakesProto.GameMessage.JoinMsg.newBuilder()
                .setGameName(gameName)
                .setPlayerName(myName)
                .setRequestedRole(SnakesProto.NodeRole.NORMAL)
                .build();
        return msg_seq;
    }


    public void sendError(String noSpaceOrGameFull, long msgSeq) {
        lastSendTime.set(System.currentTimeMillis());
    }

    public void sendChangeRole(int senderId, SnakesProto.NodeRole senderRole, int recId, SnakesProto.NodeRole recRole) {
        lastSendTime.set(System.currentTimeMillis());
    }


    public void sendPing() {
        if (System.currentTimeMillis() - lastSendTime.get() > RESEND_TIMEOUT_MS) {
            //TODO сформировать msg
            unicastService.send();
        }
    }

    public void broadcastState(SnakesProto.GameState nextState) {
        lastSendTime.set(System.currentTimeMillis());
    }

    public void broadcastAnnouncement(SnakesProto.GameState nextState, SnakesProto.GameConfig gameConfig) {
        lastSendTime.set(System.currentTimeMillis());
    }

    public void broadcastChangeMaster(int masterId) {
        lastSendTime.set(System.currentTimeMillis());
    }

    public void onAckReceived(long msgSeq) {
        SnakesProto.GameMessage removed = unconfirmedMessages.remove(msgSeq);
        if (removed == null) {
            unconfirmedMessages.put(msgSeq, null);
        }
    }

    private void resend(SnakesProto.GameMessage msg) {
        if (msg == null) return;
        unicastService.send(msg);
    }

    public long getNextSeq() {
        return seqCounter.addAndGet(1);
    }

    public void setStateDelay(int stateDelayMs) {
        stateDelay = stateDelayMs;
        receivedMessages.clear();
        unconfirmedMessages.clear();
    }



    public void handleMessage(SnakesProto.GameMessage message, InetAddress address, int port) {
    }
}
