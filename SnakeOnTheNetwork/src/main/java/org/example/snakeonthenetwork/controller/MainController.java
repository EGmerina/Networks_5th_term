package org.example.snakeonthenetwork.controller;

import javafx.application.Platform;
import me.ippolitov.fit.snakes.SnakesProto;
import org.example.snakeonthenetwork.engine.GameEngine;
import org.example.snakeonthenetwork.network.NetworkController;
import org.example.snakeonthenetwork.ui.SnakeApp;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

    private ScheduledExecutorService gameTimer;
    private final Map<Integer, SnakesProto.Direction> movesOfPlayers = new ConcurrentHashMap<>();
    private static final Logger logger = LogManager.getLogger(MainController.class);

    public MainController(SnakeApp app) {
        this.app = app;
        this.network = new NetworkController(this);
        this.network.start();
        this.myRole = SnakesProto.NodeRole.NORMAL;
    }

    public void startNewGame(SnakesProto.GameConfig config, String gameName) {
        this.gameConfig = config;
        this.myRole = SnakesProto.NodeRole.MASTER;
        this.myId = 1;
        this.masterId = 1;
        this.gameName = gameName;

        this.engine = new GameEngine(config, gameName);

        this.gameState = engine.createInitialState();

        startGameLoop();
        app.showGame(config, gameName);
        app.updateGameState(gameState);
    }

    public void onMessageReceived(SnakesProto.GameMessage msg, String senderIp, int senderPort) {
        if (msg.hasState()) {
            SnakesProto.GameState newState = msg.getState().getState();

            // Если пакет свежий
            if (gameState == null || newState.getStateOrder() > gameState.getStateOrder()) {
                this.gameState = newState;

                // ВАЖНО: Обновляем UI в потоке JavaFX
             //   Platform.runLater(() -> app.handleGameState(newState));
            }
        }

        // B. Если кто-то хочет повернуть (SteerMsg)
        else if (msg.hasSteer()) {
            if (myRole == SnakesProto.NodeRole.MASTER) {
                // Запоминаем, кто куда хочет, до следующего тика
                int playerId = msg.getSenderId();
                movesOfPlayers.put(playerId, msg.getSteer().getDirection());
            }
        }

        // C. Если кто-то хочет присоединиться (JoinMsg)
        else if (msg.hasJoin()) {
            if (myRole == SnakesProto.NodeRole.MASTER) {
                // Спрашиваем движок, есть ли место
                // Если да -> добавляем змею -> шлем AckMsg с новым ID
                // Если нет -> шлем ErrorMsg
            }
        }

        // D. Анонсы игр (AnnouncementMsg)
        else if (msg.hasAnnouncement()) {
            // Передаем в лобби
            Platform.runLater(() -> app.handleAnnouncement(msg.getAnnouncement().getGamesList()));
        }

        // ... Обработка Ack, Ping, Error ...
    }

    // ==========================================
    // 4. Игровой цикл (Только для MASTER)
    // ==========================================
    private void startGameLoop() {
        if (gameTimer != null) gameTimer.shutdown();
        gameTimer = Executors.newSingleThreadScheduledExecutor();

        gameTimer.scheduleAtFixedRate(() -> {
            try {
                // 1. Движок считает новый кадр на основе старого и накопленных поворотов
                SnakesProto.GameState nextState = engine.update(gameState, movesOfPlayers);

                // 2. Обновляем текущее состояние
                this.gameState = nextState;
                this.movesOfPlayers.clear(); // Очищаем буфер ходов

                // 3. Рассылаем всем игрокам (Broadcast)
                network.broadcastState(nextState);

                // 4. Себе тоже рисуем
                //Platform.runLater(() -> app.handleGameState(nextState));

                // 5. Раз в секунду шлем Announcement (чтобы нас видели в лобби)
                network.sendAnnouncement(nextState, gameConfig);

            } catch (Exception e) {
                e.printStackTrace(); // Чтобы таймер не сдох молча
            }
        }, 0, gameConfig.getStateDelayMs(), TimeUnit.MILLISECONDS);
    }

    // ==========================================
    // 5. Управление от игрока
    // ==========================================
    public void sendSteer(SnakesProto.Direction dir) {
        if (myRole == SnakesProto.NodeRole.MASTER) {
            // Если я Мастер, мне не надо слать по сети, я просто кладу себе в буфер
            movesOfPlayers.put(myId, dir);
        } else {
            // Если я Обычный, шлю сообщение Мастеру
            network.sendSteer(dir, masterId);
        }
    }

    public void stop() {
        if (network != null) network.stop();
        if (gameTimer != null) gameTimer.shutdown();
    }

    // Геттеры для UI
    public int getMyId() {
        return myId;
    }

    public void stopCurrentGame() {
    }

    public void joinGame(SnakesProto.GameAnnouncement announcement) {
    }
}

