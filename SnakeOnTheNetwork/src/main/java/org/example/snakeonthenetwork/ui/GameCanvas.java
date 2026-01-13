package org.example.snakeonthenetwork.ui;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import me.ippolitov.fit.snakes.SnakesProto;
import org.example.snakeonthenetwork.utils.GameField;
import org.example.snakeonthenetwork.utils.StateConverter;

import java.util.Random;
import java.util.random.RandomGenerator;

public class GameCanvas {
    private int width = 30;
    private int height = 30;
    private static final Random random = new Random();
    private SnakeApp app;

    private Color[] colorsForOtherPlayers = {Color.YELLOW, Color.BLUE, Color.AZURE, Color.ALICEBLUE, Color.ANTIQUEWHITE, Color.BISQUE, Color.PINK, Color.GRAY};

    public void setDimensions(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void setApp(SnakeApp app) {
        this.app = app;
    }

    public void drawField(Canvas canvas, SnakesProto.GameState state) {
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        GraphicsContext gc = canvas.getGraphicsContext2D();

        // Очистка фона
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, w, h);

        // 1. Вычисляем размер клетки так, чтобы она была КВАДРАТНОЙ
        // Берем минимум между доступной шириной и высотой, деленной на кол-во клеток
        double cellW = w / width;
        double cellH = h / height;
        double cellSize = Math.min(cellW, cellH);

        // 2. Вычисляем отступы, чтобы поле было по ЦЕНТРУ
        double fieldPixelWidth = cellSize * width;
        double fieldPixelHeight = cellSize * height;
        double offsetX = (w - fieldPixelWidth) / 2;
        double offsetY = (h - fieldPixelHeight) / 2;

        // Рисуем границы игрового поля (опционально, чтобы видеть границы)
        gc.setStroke(Color.DARKGRAY);
        gc.strokeRect(offsetX, offsetY, fieldPixelWidth, fieldPixelHeight);

        // --- Рисуем еду ---
        gc.setFill(Color.RED);
        for (SnakesProto.GameState.Coord food : state.getFoodsList()) {
            drawCell(gc, food.getX(), food.getY(), cellSize, offsetX, offsetY);
        }

        // --- Рисуем змей ---
        for (SnakesProto.GameState.Snake snake : state.getSnakesList()) {
            gc.setFill(snake.getPlayerId() == app.getMyId() ? Color.GREEN : colorsForOtherPlayers[snake.getPlayerId() % colorsForOtherPlayers.length]);

            int x = 0;
            int y = 0;
            boolean head = true;

            for (SnakesProto.GameState.Coord point : snake.getPointsList()) {
                if (head) {
                    x = point.getX();
                    y = point.getY();
                    head = false;
                } else {
                    x = GameField.getTorX(x + point.getX(), width);
                    y = GameField.getTorY(y + point.getY(), height);
                }
                drawCell(gc, x, y, cellSize, offsetX, offsetY);
            }
        }
    }

    // Вспомогательный метод для рисования квадратика с учетом смещения
    private void drawCell(GraphicsContext gc, int x, int y, double size, double offX, double offY) {
        // +1 к координатам и -2 к размеру делают маленький отступ между клетками (красивая сетка)
        // Если хотите сплошные линии, уберите +1 и -2
        gc.fillRect(offX + x * size + 1, offY + y * size + 1, size - 2, size - 2);
    }


}
