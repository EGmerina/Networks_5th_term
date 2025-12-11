package org.example.snakeonthenetwork.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Window;
import me.ippolitov.fit.snakes.SnakesProto;
import org.example.snakeonthenetwork.controller.MainController;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

public class SnakeApp extends Application {
    private Stage primaryStage;
    private GameController gameController;
    private MenuController menuController;
    private MainController mainController;
    private final HashMap<String, SnakesProto.GameAnnouncement> availableGames = new HashMap<>();

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
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void showGame(SnakesProto.GameConfig config, String name) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/game-view.fxml"));
            Parent root = loader.load();

            GameController controller = loader.getController();
            controller.setApp(this);
            controller.setConfig(config);
            controller.setName(name);
            this.gameController = controller;
            this.menuController = null;

            primaryStage.setScene(new Scene(root));
            primaryStage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int getMyId() {
        return mainController.getMyId();
    }

    public void startNewGame(SnakesProto.GameConfig config, String name) {
        mainController.startNewGame(config, name);
        showGame(config, name);
    }

    public void joinGame(SnakesProto.GameAnnouncement announcement) {
        mainController.joinGame(announcement);
        showGame(announcement.getConfig(), announcement.getGameName());
    }

    public void stopGame() {
        mainController.stopCurrentGame();
        showMenu();
    }

    public void updateGameState(SnakesProto.GameState gameState) {
        if (gameController == null) {
            return;
        }
        gameController.updateGameState(gameState);
    }

    public void handleAnnouncement(List<SnakesProto.GameAnnouncement> games) {
        if (menuController == null) {
            return;
        }
        menuController.updateGameList(games);
    }

    public Window getPrimaryStage() {
        return primaryStage;
    }

    public void moveSnake(SnakesProto.Direction direction) {
        mainController.sendSteer(direction);
    }
}
