package org.example.snakeonthenetwork.ui;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import me.ippolitov.fit.snakes.SnakesProto.GameConfig;

public class ConfigController {

    @FXML private TextField widthField;
    @FXML private TextField heightField;
    @FXML private TextField foodField;
    @FXML private TextField delayField;
    @FXML private TextField gameNameField;
    @FXML private TextField playerNameField;
    @FXML private Label errorLabel;

    private SnakeApp app;
    private Stage dialogStage;

    public void setApp(SnakeApp app, Stage dialogStage) {
        this.app = app;
        this.dialogStage = dialogStage;
    }

    @FXML
    private void handleStart() {
        try {
            int width = Integer.parseInt(widthField.getText());
            int height = Integer.parseInt(heightField.getText());
            int food = Integer.parseInt(foodField.getText());
            int delay = Integer.parseInt(delayField.getText());
            String gameName = gameNameField.getText();
            String playerName = playerNameField.getText();

            if (width < 10 || width > 100) throw new IllegalArgumentException("Width must be 10-100");
            if (height < 10 || height > 100) throw new IllegalArgumentException("Height must be 10-100");
            if (food < 0 || food > 100) throw new IllegalArgumentException("Food must be 0-100");
            if (delay < 100 || delay > 3000) throw new IllegalArgumentException("Delay must be 100-3000");
            if (gameName.isEmpty()) throw new IllegalArgumentException("Game name cannot be empty");

            GameConfig config = GameConfig.newBuilder()
                    .setWidth(width)
                    .setHeight(height)
                    .setFoodStatic(food)
                    .setStateDelayMs(delay)
                    .build();

            app.startNewGame(config, gameName, playerName);
            dialogStage.close();

        } catch (NumberFormatException e) {
            errorLabel.setText("Error: Please enter valid numbers");
        } catch (IllegalArgumentException e) {
            errorLabel.setText("Error: " + e.getMessage());
        }
    }

    @FXML
    private void handleCancel() {
        dialogStage.close();
    }
}
