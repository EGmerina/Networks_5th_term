package org.example.snakeonthenetwork.engine;

import me.ippolitov.fit.snakes.SnakesProto;

import java.util.Map;

public class GameEngine {
    public GameEngine(SnakesProto.GameConfig config) {
    }

    public SnakesProto.GameState createInitialState(SnakesProto.GameConfig config, String gameName) {
        return null;
    }

    public SnakesProto.GameState update(SnakesProto.GameState gameState, Map<Integer, SnakesProto.Direction> movesOfPlayers) {
        return null;
    }
}
