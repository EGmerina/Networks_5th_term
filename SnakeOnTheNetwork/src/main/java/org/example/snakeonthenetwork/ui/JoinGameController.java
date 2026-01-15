package org.example.snakeonthenetwork.ui;

import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import me.ippolitov.fit.snakes.SnakesProto.GameAnnouncement;
import me.ippolitov.fit.snakes.SnakesProto.GameConfig;
import me.ippolitov.fit.snakes.SnakesProto.NodeRole;

public class JoinGameController {

    @FXML private TextField gameNameField;
    @FXML private Label configDetailsLabel; // Для отображения размеров и еды одной строкой
    @FXML private TextField playerNameField;
    @FXML private ComboBox<NodeRole> roleComboBox;
    @FXML private Label errorLabel;

    private SnakeApp app;
    private Stage dialogStage;
    private GameAnnouncement targetGame;

    public void setApp(SnakeApp app, Stage dialogStage) {
        this.app = app;
        this.dialogStage = dialogStage;
    }

    /**
     * Инициализация данных из выбранной игры
     */
    public void setGameInfo(GameAnnouncement game) {
        this.targetGame = game;

        // Заполняем поля (read-only)
        gameNameField.setText(game.getGameName());

        GameConfig config = game.getConfig();
        String details = String.format("%dx%d | Food: %d | Delay: %d ms",
                config.getWidth(),
                config.getHeight(),
                config.getFoodStatic(),
                config.getStateDelayMs());
        configDetailsLabel.setText(details);

        // Настраиваем выбор роли
        roleComboBox.getItems().setAll(NodeRole.NORMAL, NodeRole.VIEWER);
        roleComboBox.setValue(NodeRole.NORMAL); // По умолчанию
    }

    @FXML
    private void handleJoin() {
        String playerName = playerNameField.getText().trim();
        NodeRole selectedRole = roleComboBox.getValue();

        if (playerName.isEmpty()) {
            errorLabel.setText("Please enter a player name");
            return;
        }

        if (selectedRole == null) {
            errorLabel.setText("Please select a role");
            return;
        }

        // Вызываем метод входа в MainController / App
        app.joinGame(targetGame, playerName, selectedRole);
        dialogStage.close();
    }

    @FXML
    private void handleCancel() {
        dialogStage.close();
    }
}