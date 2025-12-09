package org.example.snakeonthenetwork.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class SnakeApp extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        stage.setTitle("Net Snakes");
        stage.show();
    }

    public void showMenu() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/menu-view.fxml"));
            Parent root = loader.load();

            MenuController controller = loader.getController();
            controller.setApp(this); // Даем контроллеру доступ к главным методам

            primaryStage.setScene(new Scene(root));
            primaryStage.show();
        } catch (IOException e) { e.printStackTrace(); }
    }

    public void showGame() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/game-view.fxml"));
            Parent root = loader.load();

            GameController controller = loader.getController();
            controller.setApp(this);

            // Сохраняем ссылку на controller, чтобы передавать ему обновления стейта
            this.currentGameController = controller;

            primaryStage.setScene(new Scene(root));
            root.requestFocus(); // Важно для клавиатуры!
            primaryStage.show();
        } catch (IOException e) { e.printStackTrace(); }
    }
}
