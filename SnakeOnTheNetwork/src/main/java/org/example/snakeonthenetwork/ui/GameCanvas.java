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

        double cellSizeX = canvas.getWidth() / width;
        double cellSizeY = canvas.getHeight() / height;

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        gc.setFill(Color.RED);
        for (SnakesProto.GameState.Coord food : state.getFoodsList()) {
            gc.fillRect(food.getX() * cellSizeX, food.getY() * cellSizeY, cellSizeX, cellSizeY); // TODO проверить правильность аргументов
        }

        for (SnakesProto.GameState.Snake snake : state.getSnakesList()) {

            gc.setFill(snake.getPlayerId() == app.getMyId() ? Color.GREEN : colorsForOtherPlayers[random.nextInt(colorsForOtherPlayers.length)]);

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
                gc.fillRect(x * cellSizeX, y * cellSizeY, cellSizeX, cellSizeY);
            }
        }
    }


}
