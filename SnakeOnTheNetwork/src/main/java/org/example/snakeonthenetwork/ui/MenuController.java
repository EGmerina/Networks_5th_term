package org.example.snakeonthenetwork.ui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import me.ippolitov.fit.snakes.SnakesProto;
import me.ippolitov.fit.snakes.SnakesProto.GameAnnouncement;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;

public class MenuController {
    private static final Logger logger = LogManager.getLogger(MenuController.class);
    private final HashMap<String, GameAnnouncement> availableGames = new HashMap<>(); //предполагается что не будет 300500 игр одновремено
    //TODO проверятьь на одинаковые имена
    @FXML
    private ListView<String> gamesList; // Храним строки или объекты игр
    @FXML
    private Label statusLabel;

    private SnakeApp app;

    public void setApp(SnakeApp app) {
        this.app = app;
    }

    public void updateGameList(Collection<DiscoveredGame> games) {
        Platform.runLater(() -> {
            String selectedItem = gamesList.getSelectionModel().getSelectedItem();
            gamesList.getItems().clear();

            for (DiscoveredGame dgame : games) {
                GameAnnouncement game = dgame.announcement();
                String gameInfo = game.getGameName() + " | Players: " + game.getPlayers().getPlayersCount();

                // Проверяем дубликаты
                if (!gamesList.getItems().contains(gameInfo)) {
                    gamesList.getItems().add(gameInfo);
                    availableGames.put(gameInfo, game);
                }
            }

            if (selectedItem != null && gamesList.getItems().contains(selectedItem)) {
                gamesList.getSelectionModel().select(selectedItem);
            }
        });
    }

    @FXML
    protected void handleCreateGame() {
        logger.info("creating new game....");
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/config-view.fxml"));
            Parent root = loader.load();

            // Создаем новое всплывающее окно
            Stage dialogStage = new Stage();
            dialogStage.setTitle("New Game Settings");
            dialogStage.initModality(Modality.WINDOW_MODAL); // Блокирует главное окно
            dialogStage.initOwner(app.getPrimaryStage());
            // ЯВНО ЗАДАЕМ РАЗМЕР
            dialogStage.setScene(new Scene(root, 500, 400));
            dialogStage.setResizable(false);
            // Настраиваем контроллер
            ConfigController controller = loader.getController();
            controller.setApp(app, dialogStage);

            dialogStage.showAndWait(); // Ждем, пока пользователь заполнит и закроет

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    protected void handleJoinGame() {
        String selected = gamesList.getSelectionModel().getSelectedItem();

        if (selected == null) {
            statusLabel.setText("Please select a game first!");
            return;
        }

        GameAnnouncement announcement = availableGames.get(selected);
        if (announcement == null) {
            statusLabel.setText("Game info not found!");
            return;
        }

        // Проверяем, есть ли смысл пытаться входить (хотя Viewer может зайти всегда, если конфиг позволяет)
        // Но базовая проверка на переполнение для игроков не помешает, если вы ее реализуете
        if (!announcement.getCanJoin()) {
            // Можно разрешить входить VIEWER-ом даже если canJoin=false (зависит от логики сервера)
            // Но пока оставим предупреждение
            statusLabel.setText("Game marked as not joinable (maybe full?)");
            // return; // Раскомментируйте, если хотите запретить открывать окно
        }

        logger.info("Opening join dialog for: " + announcement.getGameName());
        showJoinDialog(announcement);
    }

    private void showJoinDialog(GameAnnouncement game) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/join-game-view.fxml"));
            Parent root = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Join Game: " + game.getGameName());
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(app.getPrimaryStage());
            dialogStage.setScene(new Scene(root, 400, 300));
            dialogStage.setResizable(false);

            JoinGameController controller = loader.getController();
            controller.setApp(app, dialogStage);
            controller.setGameInfo(game); // Передаем данные об игре

            dialogStage.showAndWait();

        } catch (IOException e) {
            logger.error("Failed to load join dialog", e);
            e.printStackTrace();
        }
    }

    public void showError(String s) {
        statusLabel.setText(s);
    }
}