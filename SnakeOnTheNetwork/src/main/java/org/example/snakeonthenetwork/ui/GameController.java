package org.example.snakeonthenetwork.ui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import me.ippolitov.fit.snakes.SnakesProto.*;

public class GameController {

    private final GameCanvas gameCanvas = new GameCanvas();
    private GameConfig config;
    private String gameName;

    @FXML
    private Canvas canvas;
    @FXML
    private Label configLabel, gameNameLabel;
    @FXML
    private ListView<String> scoreList;
    @FXML
    private BorderPane rootPane;

    private SnakeApp app;

    public void setApp(SnakeApp app) {
        this.app = app;
        rootPane.requestFocus();
        gameCanvas.setApp(app);
    }

    public void setConfig(GameConfig config) {
        this.config = config;
        this.gameCanvas.setDimensions(config.getWidth(), config.getHeight());
    }

    public void setName(String name) {
        this.gameName = name;
    }


    @FXML
    public void handleKeyPressed(KeyEvent event) {
        if (app != null) {
            switch (event.getCode()) {
                case W, UP -> app.moveSnake(Direction.UP);
                case S, DOWN -> app.moveSnake(Direction.DOWN);
                case A, LEFT -> app.moveSnake(Direction.LEFT);
                case D, RIGHT -> app.moveSnake(Direction.RIGHT);
            }
        }
    }


    public void updateGameState(GameState state) {
        Platform.runLater(() -> draw(state));
    }

    private void draw(GameState state) {
        gameCanvas.drawField(canvas, state);

        gameNameLabel.setText(gameName);

        configLabel.setText("Parameters of field : " + config.getWidth() + "*" + config.getHeight() +
                "\nStatic food : " + config.getFoodStatic() +
                "\nDelay : " + config.getStateDelayMs() + " ms");

        scoreList.getItems().clear();
        for (GamePlayer player : state.getPlayers().getPlayersList()) {
            scoreList.getItems().add(player.getName() + " (" + player.getRole() + ") " + ": " + player.getScore());
        }
    }

    @FXML
    protected void handleExit() {
        if (app != null) {
            app.stopGame();
        }
    }


}