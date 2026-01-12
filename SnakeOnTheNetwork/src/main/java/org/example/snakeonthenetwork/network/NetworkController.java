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
    private static int resendDelayMs = 100;

    private final MainController controller;

    private MulticastService multicastService;
    private UnicastService unicastService;

    private ScheduledExecutorService resendTimer = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> resendTask = null;

    private final AtomicLong seqCounter = new AtomicLong(0);
    private final Map<Long, UnconfirmedMessage> unconfirmedMessages = new ConcurrentHashMap<>();
    private final Map<Integer, Long> receivedMessages = new ConcurrentHashMap<>(); //для проверки повторных пакетов
    private final Map<Integer, InetSocketAddress> playersAddresses = new ConcurrentHashMap<>(); // 0 - для того кто отправит join

    private AtomicLong lastSendTime = new AtomicLong(0);


    private record UnconfirmedMessage(SnakesProto.GameMessage message, SocketAddress address) {
    }

    public NetworkController(MainController mainController) {
        this.controller = mainController;
        multicastService = new MulticastService(this);
        unicastService = new UnicastService(this);
    }

    public void start() {
        multicastService.start();
        unicastService.start();
        startResendTask();
    }

    private void startResendTask() {
        if (resendTask != null) {
            resendTask.cancel(true);
        }
        resendTask = resendTimer.scheduleAtFixedRate(() -> {
            unconfirmedMessages.forEach((seq, umsg) -> {
                if (umsg.message == null) return;
                unicastService.send(umsg.message, umsg.address);
            });
        }, 0, resendDelayMs, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        multicastService.stop();
        unicastService.stop();
        unconfirmedMessages.clear();
        receivedMessages.clear();
        resendTask.cancel(true);
        resendTask = null;
    }

    public void sendSteer(SnakesProto.Direction dir) {
        SnakesProto.GameMessage msg = SnakesProto.GameMessage.newBuilder()
                .setMsgSeq(seqCounter.incrementAndGet())
                .setSenderId(controller.getMyId())
                .setReceiverId(controller.getMasterId())
                .setSteer(SnakesProto.GameMessage.SteerMsg.newBuilder().setDirection(dir).build())
                .build();
        sendUnicastReliably(msg, playersAddresses.get(controller.getMasterId()));
        lastSendTime.set(System.currentTimeMillis());
    }

    public void sendAck(long msgSeq, int recId) {
        SnakesProto.GameMessage msg = SnakesProto.GameMessage.newBuilder()
                .setMsgSeq(msgSeq)
                .setSenderId(controller.getMyId())
                .setReceiverId(recId)
                .setAck(SnakesProto.GameMessage.AckMsg.newBuilder().build())
                .build();
        unicastService.send(msg, playersAddresses.get(recId));
        lastSendTime.set(System.currentTimeMillis());
    }


    public long sendJoin(SnakesProto.GameAnnouncement announcement, String playerName, SnakesProto.NodeRole role) {
        long seq = seqCounter.incrementAndGet();

        SnakesProto.GamePlayer master = null;
        for (SnakesProto.GamePlayer player : announcement.getPlayers().getPlayersList()) {
            if (player.getRole() == SnakesProto.NodeRole.MASTER) {
                master = player;
            }
        }
        if (master == null) {
            logger.error("No master in game {}", announcement.getGameName());
            return -1;
        }

        SnakesProto.GameMessage.JoinMsg join = SnakesProto.GameMessage.JoinMsg.newBuilder()
                .setGameName(announcement.getGameName())
                .setPlayerName(playerName)
                .setRequestedRole(role)
                .build();

        SnakesProto.GameMessage msg = SnakesProto.GameMessage.newBuilder()
                .setMsgSeq(seq)
                .setJoin(join)
                .build();

        sendUnicastReliably(msg, new InetSocketAddress(master.getIpAddress(), master.getPort()));
        return seq; // Возвращаем seq, чтобы MainController мог ждать конкретный Ack
    }


    public void sendError(String error) {
        SnakesProto.GameMessage msg = SnakesProto.GameMessage.newBuilder()
                .setMsgSeq(seqCounter.incrementAndGet())
                .setSenderId(controller.getMyId())
                .setError(SnakesProto.GameMessage.ErrorMsg.newBuilder().setErrorMessage(error).build())
                .build();
        unicastService.send(msg, playersAddresses.get(0));
        lastSendTime.set(System.currentTimeMillis());
    }

    public void sendChangeRole(SnakesProto.NodeRole senderRole, int recId, SnakesProto.NodeRole recRole) {
        SnakesProto.GameMessage.RoleChangeMsg roleChange = SnakesProto.GameMessage.RoleChangeMsg.newBuilder()
                .setSenderRole(senderRole)
                .setReceiverRole(recRole)
                .build();

        SnakesProto.GameMessage msg = SnakesProto.GameMessage.newBuilder()
                .setMsgSeq(seqCounter.incrementAndGet())
                .setSenderId(controller.getMyId())
                .setReceiverId(recId)
                .setRoleChange(roleChange)
                .build();
        unicastService.send(msg, playersAddresses.get(recId));
        lastSendTime.set(System.currentTimeMillis());
    }


    public void sendPing() {
        if (System.currentTimeMillis() - lastSendTime.get() > resendDelayMs) {
            SnakesProto.GameMessage msg = SnakesProto.GameMessage.newBuilder()
                    .setMsgSeq(seqCounter.incrementAndGet())
                    .setSenderId(controller.getMyId())
                    .setPing(SnakesProto.GameMessage.PingMsg.newBuilder().build())
                    .build();
            unicastService.send(msg, playersAddresses.get(controller.getMasterId()));
            lastSendTime.set(System.currentTimeMillis());
        }
    }

    public void broadcastState(SnakesProto.GameState state) {
        SnakesProto.GameMessage msg = SnakesProto.GameMessage.newBuilder()
                .setMsgSeq(seqCounter.incrementAndGet())
                .setSenderId(controller.getMyId())
                .setState(SnakesProto.GameMessage.StateMsg.newBuilder().setState(state).build())
                .build();

        broadcast(msg);
        lastSendTime.set(System.currentTimeMillis());
    }

    public void broadcastAnnouncement(String gameName, SnakesProto.GameConfig gameConfig, SnakesProto.GameState gameState) {
        SnakesProto.GameAnnouncement announcement = SnakesProto.GameAnnouncement.newBuilder()
                .setGameName(gameName)
                .setConfig(gameConfig)
                .setPlayers(gameState.getPlayers())
                .setCanJoin(true)           //TODO спрашивать у engine
                .build();

        SnakesProto.GameMessage msg = SnakesProto.GameMessage.newBuilder()
                .setMsgSeq(seqCounter.incrementAndGet())
                .setSenderId(controller.getMyId())
                .setAnnouncement(SnakesProto.GameMessage.AnnouncementMsg.newBuilder().addGames(announcement).build())
                .build();

        multicastService.send(msg);
        lastSendTime.set(System.currentTimeMillis());
    }

    public void broadcastChangeMaster() {
        SnakesProto.GameMessage.RoleChangeMsg roleChange = SnakesProto.GameMessage.RoleChangeMsg.newBuilder()
                .setSenderRole(SnakesProto.NodeRole.MASTER)
                .build();

        SnakesProto.GameMessage msg = SnakesProto.GameMessage.newBuilder()
                .setMsgSeq(seqCounter.incrementAndGet())
                .setSenderId(controller.getMyId())
                .setRoleChange(roleChange)
                .build();
        broadcast(msg);
        lastSendTime.set(System.currentTimeMillis());
    }

    public void sendDiscover() {
        SnakesProto.GameMessage msg = SnakesProto.GameMessage.newBuilder()
                .setMsgSeq(seqCounter.incrementAndGet())
                .setDiscover(SnakesProto.GameMessage.DiscoverMsg.newBuilder().build())
                .build();

        multicastService.send(msg);
    }


    private void sendUnicastReliably(SnakesProto.GameMessage msg, InetSocketAddress address) {
        unicastService.send(msg, address);
        unconfirmedMessages.put(msg.getMsgSeq(), new UnconfirmedMessage(msg, address));
    }


    private void broadcast(SnakesProto.GameMessage msg) {
        playersAddresses.forEach((ind, addr) -> {
            unicastService.send(msg, addr);
        });
    }


    public void updateStateDelay(int stateDelayMs) {
        resendDelayMs = Math.max(100, stateDelayMs / 10);
        receivedMessages.clear();
        unconfirmedMessages.clear();
        startResendTask();
    }


    public void handleMessage(SnakesProto.GameMessage message, InetAddress ip, int port) {
        InetSocketAddress senderAddr = new InetSocketAddress(ip, port);

        if (message.hasSenderId() && isDuplicate(message.getSenderId(), message.getMsgSeq())) {
            return;
        }

        if (message.hasAck()) {
            UnconfirmedMessage removed = unconfirmedMessages.remove(message.getMsgSeq());
            if (removed.message == null) {
                unconfirmedMessages.put(message.getMsgSeq(), null);
            }
        }

        if (message.hasSenderId()) {
            receivedMessages.put(message.getSenderId(), message.getMsgSeq());
            playersAddresses.put(message.getSenderId(), senderAddr);
        }

        controller.onMessageReceived(message);
    }

    private boolean isDuplicate(int senderId, long msgSeq) {
        if (!receivedMessages.containsKey(senderId) || receivedMessages.get(senderId) < msgSeq) {
            return false;
        }
        return true;
    }
}
