package org.example.snakeonthenetwork.utils;

import javafx.scene.paint.Color;
import me.ippolitov.fit.snakes.SnakesProto;

import static org.example.snakeonthenetwork.utils.CellType.FOOD;
import static org.example.snakeonthenetwork.utils.CellType.SNAKE_BODY;

public class StateConverter {

    public static GameField convert(SnakesProto.GameState gameState, int width, int height) {
        GameField gameField = new GameField(width, height);
        for (SnakesProto.GameState.Coord food : gameState.getFoodsList()) {
            gameField.setCell(food.getX(), food.getY(), FOOD);
        }
        for (SnakesProto.GameState.Snake snake : gameState.getSnakesList()) { // тут лучше всего везде ставить type SNAKE_BODY, будем смотреть коллизии в engine путем сравнения голов, точнее голову вообще игнорируем 😉
            int x = 0;
            int y = 0;
            boolean head = true;
            for (SnakesProto.GameState.Coord point : snake.getPointsList()) {
                if (head) {
                    x = point.getX();
                    y = point.getY();
                    head = false;
                } else {
                    x = (x + point.getX());
                    y = (y + point.getY());
                    gameField.setCell(x, y, SNAKE_BODY);
                }
            }
        }
        return gameField;
    }


}
