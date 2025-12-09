package org.example.snakeonthenetwork.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class SnakeApp extends Application {
    private Stage primaryStage;
    private GameController gameController; // Ссылка, чтобы передавать туда сообщения из сети
    private MenuController menuController;

    @Override
    public void start(Stage stage) throws IOException {
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

    public void showGame() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/game-view.fxml"));
            Parent root = loader.load();

            GameController controller = loader.getController();
            controller.setApp(this);
            this.gameController = controller;
            this.menuController = null;

            primaryStage.setScene(new Scene(root));
            primaryStage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int getMyId() {
        return 1; //TODO write getMyId()
    }

    public void startNewGame() {
    }

    public void joinGame(String selected) {
    }

    public void stopGame() {
    }
}
