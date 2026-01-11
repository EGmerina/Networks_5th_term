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
            dialogStage.setScene(new Scene(root));

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
        if (selected != null) {
            logger.info("Joining: " + selected);
            GameAnnouncement announcement = availableGames.get(selected);
            if (app != null && announcement.getCanJoin()) {
                app.joinGame(announcement);
                availableGames.clear();
            } else {
                statusLabel.setText("too many players!");
            }
        } else {
            statusLabel.setText("Please select a game first!");
        }
    }

    public void showError(String s) {
        statusLabel.setText(s);
    }
}