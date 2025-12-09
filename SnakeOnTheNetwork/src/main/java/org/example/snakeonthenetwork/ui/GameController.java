package org.example.snakeonthenetwork.ui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import me.ippolitov.fit.snakes.SnakesProto.*; // Твой протобуф

public class GameController {

    @FXML
    private Canvas gameCanvas;
    @FXML
    private ListView<String> scoreList;
    @FXML
    private BorderPane rootPane;

    private SnakesApp app;
    private static final int CELL_SIZE = 20; // Размер одной клетки в пикселях

    public void setApp(SnakesApp app) {
        this.app = app;
        // Фокус нужен, чтобы ловить нажатия клавиш
        rootPane.requestFocus();
    }

    @FXML
    public void handleKeyPressed(KeyEvent event) {
        if (app != null) {
            switch (event.getCode()) {
                case W, UP -> app.sendSteerMessage(Direction.UP);
                case S, DOWN -> app.sendSteerMessage(Direction.DOWN);
                case A, LEFT -> app.sendSteerMessage(Direction.LEFT);
                case D, RIGHT -> app.sendSteerMessage(Direction.RIGHT);
            }
        }
    }

    // Этот метод вызывается из сетевого потока, когда пришел StateMsg
    public void updateGameState(GameState state) {
        Platform.runLater(() -> draw(state));
    }

    private void draw(GameState state) {
        GraphicsContext gc = gameCanvas.getGraphicsContext2D();

        // 1. Очистка фона
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, gameCanvas.getWidth(), gameCanvas.getHeight());

        // 2. Рисуем еду
        gc.setFill(Color.RED);
        for (GameState.Coord food : state.getFoodsList()) {
            gc.fillRect(food.getX() * CELL_SIZE, food.getY() * CELL_SIZE, CELL_SIZE, CELL_SIZE);
        }

        // 3. Рисуем змеек
        for (GameState.Snake snake : state.getSnakesList()) {
            // Генерация цвета для игрока (можно сделать умнее)
            gc.setFill(snake.getPlayerId() == app.getMyId() ? Color.GREEN : Color.YELLOW);

            // ВАЖНО: Тут нужно преобразовать смещения (offsets) в координаты
            // Я пишу упрощенно, у тебя должен быть метод-утилита для этого
            int x = 0;
            int y = 0;
            boolean head = true;

            for (GameState.Coord point : snake.getPointsList()) {
                if (head) {
                    x = point.getX();
                    y = point.getY();
                    head = false;
                } else {
                    // Распаковка смещений + Тор (очень упрощенно!)
                    x = (x + point.getX());
                    y = (y + point.getY());
                }
                gc.fillRect(x * CELL_SIZE, y * CELL_SIZE, CELL_SIZE, CELL_SIZE);
            }
        }

        // 4. Обновляем счет
        scoreList.getItems().clear();
        for (GamePlayer player : state.getPlayers().getPlayersList()) {
            scoreList.getItems().add(player.getName() + ": " + player.getScore());
        }
    }

    @FXML
    protected void handleExit() {
        if (app != null) {
            app.stopGame();
        }
    }
}