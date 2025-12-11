package org.example.snakeonthenetwork.ui;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import me.ippolitov.fit.snakes.SnakesProto;

public class GameCanvas {
    private int width = 30;
    private int height = 30;

    public void setDimensions(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void drawField( Canvas canvas, SnakesProto.GameState state) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        // 2. Рисуем еду
        gc.setFill(Color.RED);
        for (SnakesProto.GameState.Coord food : state.getFoodsList()) {
            gc.fillRect(food.getX() * CELL_SIZE, food.getY() * CELL_SIZE, CELL_SIZE, CELL_SIZE);
        }

        // 3. Рисуем змеек
        for (SnakesProto.GameState.Snake snake : state.getSnakesList()) {
            // Генерация цвета для игрока (можно сделать умнее)
            gc.setFill(snake.getPlayerId() == app.getMyId() ? Color.GREEN : Color.YELLOW);

            // ВАЖНО: Тут нужно преобразовать смещения (offsets) в координаты
            // Я пишу упрощенно, у тебя должен быть метод-утилита для этого
            int x = 0;
            int y = 0;
            boolean head = true;

            for (SnakesProto.GameState.Coord point : snake.getPointsList()) {
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
    }
}
