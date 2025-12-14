package org.example.snakeonthenetwork.controller;

import javafx.application.Platform;
import me.ippolitov.fit.snakes.SnakesProto;
import org.example.snakeonthenetwork.engine.GameEngine;
import org.example.snakeonthenetwork.network.NetworkController;
import org.example.snakeonthenetwork.ui.SnakeApp;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


import java.util.Map;
import java.util.concurrent.*;

public class MainController {

    private SnakeApp app;
    private NetworkController network;
    private GameEngine engine;

    private SnakesProto.GameConfig gameConfig;
    private String gameName;
    private volatile SnakesProto.GameState gameState;
    private SnakesProto.NodeRole myRole;
    private int myId;
    private int masterId;

    private final Map<Integer, Long> lastSeenNodes = new ConcurrentHashMap<>();
    private ScheduledFuture<?> timeoutCheckTask;
    private ScheduledExecutorService gameTimer;
    private final Map<Integer, SnakesProto.Direction> movesOfPlayers = new ConcurrentHashMap<>();
    private static final Logger logger = LogManager.getLogger(MainController.class);

    public MainController(SnakeApp app) {
        this.app = app;
        this.network = new NetworkController(this);
        this.network.start();
        this.myRole = SnakesProto.NodeRole.NORMAL;
    }

    public void startNewGame(SnakesProto.GameConfig config, String gameName, String playerName) {
        this.gameConfig = config;
        this.myRole = SnakesProto.NodeRole.MASTER;
        this.myId = 1;
        this.masterId = 1;
        this.gameName = gameName;

        this.engine = new GameEngine(config, gameName);

        this.gameState = engine.createInitialState(playerName);

        startGameLoop();
        app.showGame(config, gameName);
        app.updateGameState(gameState);
    }

    public void onMessageReceived(SnakesProto.GameMessage msg, String senderIp, int senderPort) {
        if (msg.hasState()) {
            SnakesProto.GameState newState = msg.getState().getState();

            if (gameState == null || newState.getStateOrder() > gameState.getStateOrder()) {
                this.gameState = newState;
                Platform.runLater(() -> app.updateGameState(newState));
            }
        } else if (msg.hasSteer()) {
            if (myRole == SnakesProto.NodeRole.MASTER) { //TODO проверять порядковый номер сообщения
                int playerId = msg.getSenderId();
                movesOfPlayers.put(playerId, msg.getSteer().getDirection());
            }
        } else if (msg.hasJoin()) {
            if (myRole == SnakesProto.NodeRole.MASTER) {
                network.sendAck(msg.getSenderId());
                //TODo шде-то надо слать  ErrorMsg если переполнение
            }
        } else if (msg.hasAnnouncement()) {
            Platform.runLater(() -> app.handleAnnouncement(msg.getAnnouncement().getGamesList()));
        } else if (msg.hasAck()) {
            network.onAckReceived(msg.getMsgSeq());
            if (msg.getMsgSeq() == lastJoinMsgSeq) {
                this.myId = msg.getReceiverId(); // Получаем наш ID, назначенный мастером
                this.myRole = SnakesProto.NodeRole.NORMAL;
                this.masterId = msg.getSenderId();

                Platform.runLater(() -> app.showGame(gameConfig, gameName)); //TODO надо понять где меняются конфиг и имя
            }
        } else if (msg.hasPing()) {
            updateNodeTimestamp(msg.getSenderId());
        } else if (msg.hasDiscover()) {
            if (myRole == SnakesProto.NodeRole.MASTER) {
                network.sendAnnouncement(gameState, gameConfig, senderIp, senderPort);
            }
        } else if (msg.hasError()) {
            String errorMessage = msg.getError().getErrorMessage();
            stop();
            Platform.runLater(() -> {
                app.showError("Server Error: " + errorMessage);
            });
        } else {
            logger.error("unknown message");
        }
    }


    private void startGameLoop() {
        if (gameTimer != null) gameTimer.shutdown();
        gameTimer = Executors.newSingleThreadScheduledExecutor();

        gameTimer.scheduleAtFixedRate(() -> {
            try {
                SnakesProto.GameState nextState = engine.update(gameState, movesOfPlayers);

                this.gameState = nextState;
                this.movesOfPlayers.clear();

                network.broadcastState(nextState);

                Platform.runLater(() -> app.updateGameState(nextState));

                network.sendAnnouncement(nextState, gameConfig, senderIp, senderPort);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, gameConfig.getStateDelayMs(), TimeUnit.MILLISECONDS);
    }


    public void sendSteer(SnakesProto.Direction dir) {
        if (myRole == SnakesProto.NodeRole.MASTER) {
            movesOfPlayers.put(myId, dir);
        } else {
            network.sendSteer(dir, masterId);
        }
    }

    public void stop() {
        if (network != null) network.stop();
        if (gameTimer != null) gameTimer.shutdown();
    }

    public int getMyId() {
        return myId;
    }

    public void joinGame(SnakesProto.GameAnnouncement announcement) {
        network.sendJoin(announcement.getGameName());
    }


    //TODO теперь тут куча фигокода...

    private void updateNodeTimestamp(int nodeId) { //TODO сделать поток
        lastSeenNodes.put(nodeId, System.currentTimeMillis());
    }

    private void startTimeoutCheck() {
        // Проверяем каждую секунду
        timeoutCheckTask = gameTimer.scheduleAtFixedRate(this::checkNodes, 1, 1, TimeUnit.SECONDS);
    }

    private void checkNodes() {
        long now = System.currentTimeMillis();
        int timeout = config.getNodeTimeoutMs();

        lastSeenNodes.forEach((id, lastSeen) -> {
            // Мы (Мастер) не проверяем самих себя
            if (id == myId) return;

            if (now - lastSeen > timeout) {
                System.out.println("Node " + id + " timed out. Handling...");
                handleNodeTimeout(id);
            }
        });
    }

    private void handleNodeTimeout(int playerId) {
        // 1. Убираем игрока из списка активных проверок
        lastSeenNodes.remove(playerId);

        // 2. Меняем роль игрока на VIEWER (наблюдатель)
        // В Protobuf мы должны найти игрока в GameState и обновить его роль
        engine.makePlayerViewer(playerId);

        // 3. Змея остается на поле (SnakeState.ALIVE), но теперь она "зомби"
        // Она будет просто ползти прямо, пока не врежется во что-нибудь.
        // В твоем движке (Engine) должна быть пометка, что у этой змеи больше нет владельца.

        // 4. Если отвалился DEPUTY (заместитель), Мастер должен назначить нового!
        if (isDeputy(playerId)) {
            assignNewDeputy();
        }
    }
}

