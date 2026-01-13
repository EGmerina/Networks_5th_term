package org.example.snakeonthenetwork.controller;

import javafx.application.Platform;
import me.ippolitov.fit.snakes.SnakesProto;
import org.example.snakeonthenetwork.engine.GameEngine;
import org.example.snakeonthenetwork.network.NetworkController;
import org.example.snakeonthenetwork.ui.SnakeApp;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.*;

public class MainController {

    private final int NUM_THREADS = 2;
    private final int ANNOUNCEMENT_DELAY = 1; //sec

    private final SnakeApp app;
    private final NetworkController network;
    private GameEngine engine;
    private static final Logger logger = LogManager.getLogger(MainController.class);


    private volatile SnakesProto.GameState gameState;
    private SnakesProto.GameConfig gameConfig;
    private String gameName;

    private volatile SnakesProto.NodeRole myRole = SnakesProto.NodeRole.NORMAL; //TODO е нужно!
    private int myId;

    private int deputyId = -1;
    private int masterId = -1;

    private ScheduledExecutorService gameScheduler; //для проверки игроков и игрового цикла
    private ScheduledFuture<?> gameLoopTask = null;
    private ScheduledFuture<?> announcementTask = null;
    private ScheduledFuture<?> timeoutTask = null;
    private ScheduledFuture<?> pingTask = null;

    private final Map<Integer, Long> lastSeenNodes = new ConcurrentHashMap<>();
    private final Map<Integer, SnakesProto.Direction> movesBuffer = new ConcurrentHashMap<>();

    // Данные для подключения (ACK на Join)
    private long lastJoinMsgSeq = -1;

    public MainController(SnakeApp app) {
        this.app = app;
        this.network = new NetworkController(this);
        this.gameScheduler = Executors.newScheduledThreadPool(NUM_THREADS);
        this.network.start();
    }

    public void startNewGame(SnakesProto.GameConfig config, String gameName, String playerName) {
        this.gameConfig = config;
        this.gameName = gameName;
        this.myRole = SnakesProto.NodeRole.MASTER;
        this.myId = 1;
        this.masterId = myId;

        this.engine = new GameEngine(config);
        this.gameState = engine.createInitialState(playerName, network.getUnicastPort());

        network.updateStateDelay(gameConfig.getStateDelayMs());
        startGameLoop();

        app.showGame(config, gameName);
        app.updateGameState(gameState);
    }

    private void startGameLoop() { //для мастера
        stopAllTasks();

        if (gameScheduler.isShutdown()) {
            gameScheduler = Executors.newScheduledThreadPool(NUM_THREADS);
        }

        startPingTask();

        gameLoopTask = gameScheduler.scheduleAtFixedRate(() -> {
            try {
                SnakesProto.GameState nextState = engine.update(gameState, movesBuffer);
                this.gameState = nextState;
                this.movesBuffer.clear();

                network.broadcastState(nextState);

                Platform.runLater(() -> app.updateGameState(nextState));

            } catch (Exception e) {
                logger.error("Game loop error", e);
            }
        }, 0, gameConfig.getStateDelayMs(), TimeUnit.MILLISECONDS);


        announcementTask = gameScheduler.scheduleAtFixedRate(() -> {
            try {
                network.broadcastAnnouncement(gameName, gameConfig, gameState);
            } catch (Exception e) {
                logger.error("Announcement error", e);
            }
        }, 0, ANNOUNCEMENT_DELAY, TimeUnit.SECONDS);

        timeoutTask = gameScheduler.scheduleAtFixedRate(this::checkNodes, 1, (long) (0.8 * gameConfig.getStateDelayMs()), TimeUnit.MILLISECONDS); //TODO тут хз какой интервал дожен быть
    }

    public void joinGame(SnakesProto.GameAnnouncement announcement, String playerName, SnakesProto.NodeRole role) {
        this.gameConfig = announcement.getConfig();
        this.gameName = announcement.getGameName();
        this.myRole = role;

        network.updateStateDelay(gameConfig.getStateDelayMs());

        lastJoinMsgSeq = network.sendJoin(announcement, playerName, myRole);
    }

    public void onMessageReceived(SnakesProto.GameMessage msg) { //вызывается из network, не проверяю роли, с надеждой на правильность отправки
        updateNodeTimestamp(msg.getSenderId()); //TODO ааааа в итоге где следить за отвалкой игроков????
        if (msg.hasAck()) {
            handleAck(msg);
            return;
        } else if (msg.hasAnnouncement()) {
            handleAnnouncement(msg);
            return;
        } else if (msg.hasDiscover()) {
            if (myRole == SnakesProto.NodeRole.MASTER) {
                network.broadcastAnnouncement(gameName, gameConfig, gameState);
            }
            return;
        } else if (msg.hasState()) {
            handleState(msg);

        } else if (msg.hasError()) { //это вместо Ack на join
            Platform.runLater(() -> app.showError(msg.getError().getErrorMessage()));
        } else if (msg.hasPing()) { //время обновилось

        } else if (msg.hasSteer()) {
            movesBuffer.put(msg.getSenderId(), msg.getSteer().getDirection());
        } else if (msg.hasJoin()) {
            handleJoinRequest(msg);
            return;
        } else if (msg.hasRoleChange()) {
            handleRoleChange(msg);
        } else {
            logger.error("Get unknown message :{}", msg);
            return;
        }
        network.sendAck(msg.getMsgSeq(), msg.getSenderId());
    }

    private void handleAnnouncement(SnakesProto.GameMessage msg) {
        SnakesProto.GameAnnouncement announcement = msg.getAnnouncement().getGamesList().getFirst();
        if (announcement.getPlayers().getPlayersCount() == 1) {
            InetSocketAddress addresAndPort = network.getPlayerAddress(1);
            var announcementBuilder = announcement.toBuilder();
            announcementBuilder.getPlayersBuilder()
                    .getPlayersBuilder(0)
                    .setIpAddress(addresAndPort.getAddress().getHostAddress());

            announcement = announcementBuilder.build();
        }
        app.handleAnnouncement(announcement);
    }

    private void handleRoleChange(SnakesProto.GameMessage msg) {
        if (!msg.getRoleChange().hasReceiverRole() && msg.getRoleChange().getSenderRole() == SnakesProto.NodeRole.MASTER) {
            masterId = msg.getSenderId();
        } else if (msg.getRoleChange().getReceiverRole() == SnakesProto.NodeRole.DEPUTY) {
            deputyId = myId;
            myRole = SnakesProto.NodeRole.DEPUTY;
            //TODO сменить gameState?
        } else if (msg.getRoleChange().getSenderRole() == SnakesProto.NodeRole.VIEWER) {
            removePlayer(msg.getSenderId());
        }
    }

    private void handleState(SnakesProto.GameMessage msg) {
        SnakesProto.GameState newState = msg.getState().getState();
        if (gameState == null || newState.getStateOrder() > gameState.getStateOrder()) {
            this.gameState = newState;
            masterId = msg.getSenderId();
            deputyId = getDeputyId();
            Platform.runLater(() -> app.updateGameState(newState));
        }
    }

    private void handleAck(SnakesProto.GameMessage msg) {
        if (msg.getMsgSeq() == lastJoinMsgSeq) {
            this.myId = msg.getReceiverId();
            this.masterId = msg.getSenderId();

            Platform.runLater(() -> app.showGame(gameConfig, gameName));
            startPingTask();
        }
    }

    private void startPingTask() {
        pingTask = gameScheduler.scheduleAtFixedRate(() -> {
            try {
                network.sendPing();
            } catch (Exception e) {
                logger.error("Ping error", e);
            }
        }, 0, gameConfig.getStateDelayMs() / 10, TimeUnit.MILLISECONDS);
    }

    private void handleJoinRequest(SnakesProto.GameMessage msg) {
        SnakesProto.GameMessage.JoinMsg join = msg.getJoin();

        int newPlayerId = engine.addPlayer(gameState, join.getPlayerName(), join.getRequestedRole());

        if (newPlayerId == -1) {
            network.sendError("No space or game full");
            return;
        }
        network.sendAck(msg.getMsgSeq(), newPlayerId);
        if (deputyId == -1) {
            assignNewDeputy();
        }
    }


    private void updateNodeTimestamp(int nodeId) {
        lastSeenNodes.put(nodeId, System.currentTimeMillis());
    }


    private void checkNodes() {
        long now = System.currentTimeMillis();
        long timeout = (long) (gameConfig.getStateDelayMs() * 0.8);

        lastSeenNodes.forEach((id, lastSeen) -> {

            if (id == myId) return;

            if (now - lastSeen > timeout) {
                logger.trace("Node " + id + " timed out. Handling...");
                handleNodeTimeout(id);
            }
        });
    }

    private void handleNodeTimeout(int playerId) {
        removePlayer(playerId);
        if (playerId == deputyId && myId == masterId) {
            assignNewDeputy();
        } else if (playerId == masterId && myId == deputyId) {
            myRole = SnakesProto.NodeRole.MASTER;
            masterId = myId;
            network.broadcastChangeMaster();
            assignNewDeputy();
            startGameLoop();
        } else if (playerId == masterId) {
            //TODO ????
        }
    }

    private void removePlayer(int playerId) {
        lastSeenNodes.remove(playerId);
        //делаем игрока viewer, пока его зомби-змейка не расшибется
        this.gameState = engine.removePlayer(gameState, playerId); // TODO сделать thread-safe!!!!!
    }

    private void assignNewDeputy() { //только для мастера
        deputyId = -1;
        for (SnakesProto.GamePlayer player : gameState.getPlayers().getPlayersList()) {
            if (player.getId() != myId && player.getRole() == SnakesProto.NodeRole.NORMAL && lastSeenNodes.containsKey(player.getId())) {
                deputyId = player.getId();
            }
        }
        if (deputyId == -1) {
            return;
        }
        me.ippolitov.fit.snakes.SnakesProto.GameState newState = engine.updateRole(gameState, deputyId, SnakesProto.NodeRole.DEPUTY);
        gameState = newState;
        network.sendChangeRole(SnakesProto.NodeRole.MASTER, deputyId, SnakesProto.NodeRole.DEPUTY);
    }

    private int getDeputyId() {
        for (SnakesProto.GamePlayer player : gameState.getPlayers().getPlayersList()) {
            if (player.getRole() == SnakesProto.NodeRole.DEPUTY) {
                return player.getId();
            }
        }
        return -1;
    }

    private void stopAllTasks() {
        if (gameLoopTask != null) {
            gameLoopTask.cancel(true);
            gameLoopTask = null;
        }

        if (announcementTask != null) {
            announcementTask.cancel(true);
            announcementTask = null;
        }

        if (timeoutTask != null) {
            timeoutTask.cancel(true);
            timeoutTask = null;
        }

        if (pingTask != null) {
            pingTask.cancel(true);
            pingTask = null;
        }

        logger.info("All tasks stopped.");
    }

    public void stopGame() {
        lastSeenNodes.clear();
        movesBuffer.clear();
        stopAllTasks();
    }

    public void sendSteer(SnakesProto.Direction direction) {
        if (myRole == SnakesProto.NodeRole.MASTER) { //TODo возможно стоит убрать myRole и оставить только myId
            movesBuffer.put(myId, direction);
        } else {
            network.sendSteer(direction);
        }
    }

    public int getMyId() {
        return myId;
    }

    public int getMasterId() {
        return masterId;
    }

    public void sendDiscover() {
        network.sendDiscover();
    }
}

