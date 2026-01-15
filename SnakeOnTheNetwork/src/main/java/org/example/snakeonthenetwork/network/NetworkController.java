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
    private final Map<Integer, InetSocketAddress> playersAddresses = new ConcurrentHashMap<>(); // 0 - для того кто отправит join (новый, неподтвержденный игрок)

    private AtomicLong lastSendTime = new AtomicLong(0); // в целом

    public void registerNewPlayer(int newPlayerId) {
        InetSocketAddress address = playersAddresses.remove(0);
        playersAddresses.put(newPlayerId, address);
    }

    public void removePlayer(int playerId) {
        playersAddresses.remove(playerId);
        unconfirmedMessages.entrySet().removeIf(entry ->
                entry.getValue().message.getReceiverId() == playerId
        );
    }


    public void stopResendTask() {
        if (resendTask != null) {
            resendTask.cancel(true);
            resendTask = null;
        }
        unconfirmedMessages.clear();
        receivedMessages.clear();
        playersAddresses.clear();
    }


    public void registerPlayer(int id, InetSocketAddress inetSocketAddress) {
        playersAddresses.put(id, inetSocketAddress);
    }

    private static class UnconfirmedMessage {
        final SnakesProto.GameMessage message;
        final SocketAddress address;
        long sendTime;

        public UnconfirmedMessage(SnakesProto.GameMessage message, SocketAddress address, long sendTime) {
            this.message = message;
            this.address = address;
            this.sendTime = sendTime;
        }
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

    public void startResendTask() {
        if (resendTask != null) return;

        resendTask = resendTimer.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            unconfirmedMessages.forEach((seq, umsg) -> {
                if (now - umsg.sendTime > resendDelayMs) {
                    unicastService.send(umsg.message, umsg.address);
                    umsg.sendTime = now;
                }
            });
        }, 0, resendDelayMs, TimeUnit.MILLISECONDS);
    }


    public void stop() {
        multicastService.stop();
        unicastService.stop();
        receivedMessages.clear();
        stopResendTask();
    }

    public void sendSteer(SnakesProto.Direction dir) {
        logger.debug("Sending steer : {}", dir);
        SnakesProto.GameMessage msg = SnakesProto.GameMessage.newBuilder()
                .setMsgSeq(seqCounter.incrementAndGet())
                .setSenderId(controller.getMyId())
                .setReceiverId(controller.getMasterId())
                .setSteer(SnakesProto.GameMessage.SteerMsg.newBuilder().setDirection(dir).build())
                .build();

        unconfirmedMessages.entrySet().removeIf(entry ->
                entry.getValue().message.hasSteer()
        );
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

        playersAddresses.put(master.getId(), new InetSocketAddress(master.getIpAddress(), master.getPort()));
        logger.debug("Master address  {}:{}", master.getIpAddress(), master.getPort());

        SnakesProto.GameMessage.JoinMsg join = SnakesProto.GameMessage.JoinMsg.newBuilder()
                .setGameName(announcement.getGameName())
                .setPlayerName(playerName)
                .setRequestedRole(role)
                .build();

        SnakesProto.GameMessage msg = SnakesProto.GameMessage.newBuilder()
                .setMsgSeq(seq)
                .setSenderId(0)
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
        logger.trace("Sending role change: myId {}, myRole {}, recId {}, recRole {}", controller.getMyId(), senderRole, recId, recRole);
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
        sendUnicastReliably(msg, playersAddresses.get(recId));
        lastSendTime.set(System.currentTimeMillis());
    }


    public void sendPing() {
        logger.trace("Try to send ping ");
        playersAddresses.forEach((playerId, address) -> {
            logger.trace("To player {}", playerId);
            if (System.currentTimeMillis() - lastSendTime.get() > resendDelayMs && playerId > 0) {
                SnakesProto.GameMessage msg = SnakesProto.GameMessage.newBuilder()
                        .setMsgSeq(seqCounter.incrementAndGet())
                        .setSenderId(controller.getMyId())
                        .setPing(SnakesProto.GameMessage.PingMsg.newBuilder().build())
                        .build();
                unicastService.send(msg, address);
                lastSendTime.set(System.currentTimeMillis());
                logger.trace("Ping was sent to id {}", playerId);
            }
        });
    }

    public void broadcastState(SnakesProto.GameState state) {
        SnakesProto.GameMessage msg = SnakesProto.GameMessage.newBuilder()
                .setMsgSeq(seqCounter.incrementAndGet())
                .setSenderId(controller.getMyId())
                .setState(SnakesProto.GameMessage.StateMsg.newBuilder().setState(state).build())
                .build();


        unconfirmedMessages.entrySet().removeIf(entry ->
                entry.getValue().message.hasState()
        );

        broadcast(msg);
        lastSendTime.set(System.currentTimeMillis());
    }

    public void broadcastAnnouncement(String gameName, SnakesProto.GameConfig gameConfig, SnakesProto.GameState
            gameState) {
        SnakesProto.GameAnnouncement announcement = SnakesProto.GameAnnouncement.newBuilder()
                .setGameName(gameName)
                .setConfig(gameConfig)
                .setPlayers(gameState.getPlayers())
                .setCanJoin(true)
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
        unconfirmedMessages.put(msg.getMsgSeq(), new UnconfirmedMessage(msg, address, System.currentTimeMillis()));
    }


    private void broadcast(SnakesProto.GameMessage msg) {
        playersAddresses.forEach((ind, addr) -> {
            if (ind > 0) {
                sendUnicastReliably(msg, addr);
            }

        });
    }


    public void updateStateDelay(int stateDelayMs) {
        resendDelayMs = Math.max(100, stateDelayMs / 10);
        receivedMessages.clear();
        unconfirmedMessages.clear();
        startResendTask();
    }


    public void handleMessage(SnakesProto.GameMessage msg, InetAddress ip, int port) {
        SnakesProto.GameMessage message = msg;
        InetSocketAddress senderAddr = new InetSocketAddress(ip, port);

        logger.debug("Received message : {} from {}", message.getTypeCase(), message.getSenderId());
        if (message.hasJoin() && playersAddresses.containsValue(senderAddr)) {
            for (Map.Entry<Integer, InetSocketAddress> entry : playersAddresses.entrySet()) {
                if (entry.getKey() > 0 && entry.getValue().equals(senderAddr)) {
                    return;
                }
            }
        }

        if (message.hasSenderId() && !message.hasJoin() && (isDuplicate(message.getSenderId(), message.getMsgSeq()) || message.getSenderId() == controller.getMyId())) {
            logger.trace("Ignore message : {} from {}", message.getTypeCase(), message.getSenderId());
            return;
        }


        if (message.hasAck()) {
            UnconfirmedMessage removed = unconfirmedMessages.remove(message.getMsgSeq());
            if (removed == null) {
                logger.trace("Ack for unknown or already confirmed message: {}", message.getMsgSeq());
            }
        }

        if (message.hasSenderId() && !message.hasDiscover() && !message.hasAck() && !message.hasPing()) {

            if (message.hasAnnouncement()) {

                SnakesProto.GameAnnouncement firstGame = message.getAnnouncement().getGamesList().getFirst();

                if (firstGame.getPlayers().getPlayersCount() == 1) {

                    var announcementMsgBuilder = message.getAnnouncement().toBuilder();

                    announcementMsgBuilder.getGamesBuilder(0)
                            .getPlayersBuilder()
                            .getPlayersBuilder(0)
                            .setIpAddress(ip.getHostAddress());

                    message = message.toBuilder()
                            .setAnnouncement(announcementMsgBuilder)
                            .build();
                }
            } else {
                playersAddresses.put(message.getSenderId(), senderAddr);
                receivedMessages.put(message.getSenderId(), message.getMsgSeq());
            }

        }

        controller.onMessageReceived(message);
    }

    private boolean isDuplicate(int senderId, long msgSeq) {
        if (!receivedMessages.containsKey(senderId) || receivedMessages.get(senderId) < msgSeq) {
            return false;
        }
        return true;
    }

    public InetSocketAddress getPlayerAddress(int playerId) {
        return playersAddresses.get(playerId);
    }

    public int getUnicastPort() {
        return unicastService.getPort();
    }


}
