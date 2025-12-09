package org.example.snakeonthenetwork.ui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import me.ippolitov.fit.snakes.SnakesProto.GameAnnouncement;

public class MenuController {

    @FXML
    private ListView<String> gamesList; // Храним строки или объекты игр
    @FXML
    private Label statusLabel;

    // Ссылка на главный контроллер приложения, чтобы вызывать сетевые методы
    private SnakesApp app;

    public void setApp(SnakesApp app) {
        this.app = app;
    }

    @FXML
    public void initialize() {
        // Тут можно запустить анимацию поиска или очистить список
    }

    // Этот метод будет вызывать сетевой слой, когда придет Multicast сообщение
    public void updateGameList(GameAnnouncement announcement) {
        Platform.runLater(() -> {
            String gameInfo = announcement.getGameName() + " | Players: " + announcement.getPlayers().getPlayersCount();
            // Простая логика: если такой игры нет, добавить.
            // В реальности лучше хранить Map<String, Announcement>
            if (!gamesList.getItems().contains(gameInfo)) {
                gamesList.getItems().add(gameInfo);
            }
        });
    }

    @FXML
    protected void handleCreateGame() {
        System.out.println("Creating new game...");
        if (app != null) {
            app.startNewGame(); // Переход в режим MASTER
        }
    }

    @FXML
    protected void handleJoinGame() {
        String selected = gamesList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            System.out.println("Joining: " + selected);
            if (app != null) {
                // Тут нужно парсить выбранную игру и отправлять JoinMsg
                app.joinGame(selected);
            }
        } else {
            statusLabel.setText("Please select a game first!");
        }
    }
}