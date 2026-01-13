package org.example.snakeonthenetwork.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import javafx.stage.Window;
import me.ippolitov.fit.snakes.SnakesProto;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.snakeonthenetwork.controller.MainController;

import java.io.IOException;
import java.sql.Time;
import java.util.HashMap;
import java.util.List;

public class SnakeApp extends Application {
    private static final Logger logger = LogManager.getLogger(SnakeApp.class);
    private static final long TIME_TO_UPDATE = 5000;
    private Stage primaryStage;
    private GameController gameController;
    private MenuController menuController;
    private MainController mainController;
    private final HashMap<String, DiscoveredGame> availableGames = new HashMap<>();

    @Override
    public void start(Stage stage) throws IOException {
        this.mainController = new MainController(this);
        this.primaryStage = stage;
        stage.setTitle("Net Snakes");
        showMenu();
    }

    public void showMenu() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/menu-view.fxml"));
            Parent root = loader.load();

            MenuController controller = loader.getController();
            controller.setApp(this);
            this.menuController = controller;
            this.gameController = null;

            primaryStage.setScene(new Scene(root));
            primaryStage.show();
            mainController.sendDiscover();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void showGame(SnakesProto.GameConfig config, String gameName) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/game-view.fxml"));
            Parent root = loader.load();

            GameController controller = loader.getController();
            controller.setApp(this);
            controller.setConfig(config);
            controller.setName(gameName);
            this.gameController = controller;
            this.menuController = null;

            Scene gameScene = new Scene(root);

            gameScene.addEventFilter(KeyEvent.KEY_PRESSED, event -> controller.handleKeyPressed(event));

            primaryStage.setScene(gameScene);
            primaryStage.show();

            root.requestFocus();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int getMyId() {
        return mainController.getMyId();
    }

    public void startNewGame(SnakesProto.GameConfig config, String name, String playerName) {
        mainController.startNewGame(config, name, playerName);
        showGame(config, name);
    }

    public void joinGame(SnakesProto.GameAnnouncement announcement, String playerName, SnakesProto.NodeRole role) {
        mainController.joinGame(announcement, playerName, role);
        showGame(announcement.getConfig(), announcement.getGameName());
    }

    public void stopGame() {
        mainController.stopGame();
        showMenu();
    }

    public void updateGameState(SnakesProto.GameState gameState) {
        if (gameController == null) {
            return;
        }
        gameController.updateGameState(gameState);
    }

    public void handleAnnouncement(SnakesProto.GameAnnouncement announcement) {
        if (menuController == null) {
            return;
        }
        Long now = System.currentTimeMillis();
        availableGames.put(announcement.getGameName(), new DiscoveredGame(announcement, now));
        availableGames.values().removeIf(game -> (now - game.lastUpdateTime()) > TIME_TO_UPDATE);
        menuController.updateGameList(availableGames.values());
    }

    public Window getPrimaryStage() {
        return primaryStage;
    }

    public void moveSnake(SnakesProto.Direction direction) {
        mainController.sendSteer(direction);
    }


    public void showError(String s) {
        showMenu();
        menuController.showError(s);
    }

    @Override
    public void stop() throws Exception {
        if (mainController != null) {
            mainController.closeGame();
        }
        super.stop();
        System.exit(0);
    }
}
