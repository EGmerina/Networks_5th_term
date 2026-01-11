package org.example.snakeonthenetwork.engine;

import me.ippolitov.fit.snakes.SnakesProto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class GameEngine {
    private SnakesProto.GameConfig config;
    private String gameName;
    private final Random random = new Random();

    public GameEngine(SnakesProto.GameConfig config) {
        this.config = config;
        this.gameName = gameName;
    }

    public SnakesProto.GameState createInitialState(String playerName) { //TODO тут надо разобраться....
        int width = config.getWidth();
        int height = config.getHeight();

        SnakesProto.GamePlayer master = SnakesProto.GamePlayer.newBuilder()
                .setName(playerName)
                .setId(1)
                .setIpAddress("")
                .setPort(0)
                .setRole(SnakesProto.NodeRole.MASTER)
                .setType(SnakesProto.PlayerType.HUMAN)
                .setScore(0)
                .build();

        SnakesProto.GamePlayers playersList = SnakesProto.GamePlayers.newBuilder()
                .addPlayers(master)
                .build();

        int headX = width / 2;
        int headY = height / 2;

        SnakesProto.GameState.Coord headCoord = SnakesProto.GameState.Coord.newBuilder()
                .setX(headX)
                .setY(headY)
                .build();

        SnakesProto.GameState.Coord tailOffset = SnakesProto.GameState.Coord.newBuilder()
                .setX(0)
                .setY(1)
                .build();

        SnakesProto.GameState.Snake masterSnake = SnakesProto.GameState.Snake.newBuilder()
                .setPlayerId(1)
                .addPoints(headCoord)    // 1. Голова
                .addPoints(tailOffset)   // 2. Хвост (как смещение!)
                .setHeadDirection(SnakesProto.Direction.UP)
                .setState(SnakesProto.GameState.Snake.SnakeState.ALIVE)
                .build();


        int foodCount = config.getFoodStatic() + 1; //TODO не совсем праильная формула

        List<SnakesProto.GameState.Coord> foods = new ArrayList<>();


        for (int i = 0; i < foodCount; i++) {
            SnakesProto.GameState.Coord food = generateRandomFreeCoord(width, height, masterSnake, foods);
            foods.add(food);
        }


        return SnakesProto.GameState.newBuilder()
                .setStateOrder(0)
                .setPlayers(playersList)
                .addSnakes(masterSnake)
                .addAllFoods(foods)
                .build();
    }


    private SnakesProto.GameState.Coord generateRandomFreeCoord(int w, int h, SnakesProto.GameState.Snake snake, List<SnakesProto.GameState.Coord> existingFoods) { //TODO делать с помощью GameGrid
        int x, y;
        boolean collision;

        do {
            x = random.nextInt(w);
            y = random.nextInt(h);
            collision = false;

            // Проверка: не попали ли в уже созданную еду
            for (SnakesProto.GameState.Coord f : existingFoods) {
                if (f.getX() == x && f.getY() == y) {
                    collision = true;
                    break;
                }
            }

            // Проверка: не попали ли в голову змеи (упрощенно, только голову проверяем)
            // По-хорошему тут нужен checkCollision со всем телом
            if (snake.getPoints(0).getX() == x && snake.getPoints(0).getY() == y) {
                collision = true;
            }

        } while (collision);

        return SnakesProto.GameState.Coord.newBuilder().setX(x).setY(y).build();
    }
    public SnakesProto.GameState update(SnakesProto.GameState gameState, Map<Integer, SnakesProto.Direction> movesOfPlayers) {
        if(movesOfPlayers ==null)
    }

    public int addPlayer(String playerName, SnakesProto.NodeRole requestedRole) {
    }

    public void makePlayerViewer(int playerId) {

    }

    public SnakesProto.GameState removePlayer(int playerId) {
        return null;
    }

    public SnakesProto.GameState updateRole(SnakesProto.GameState gameState, int deputyId, SnakesProto.NodeRole nodeRole) {
    }
}
