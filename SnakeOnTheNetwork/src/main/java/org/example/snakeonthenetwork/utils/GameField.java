package org.example.snakeonthenetwork.utils;

import java.util.Arrays;

public class GameField {
    private final int width;
    private final int height;
    private final CellType[][] grid;

    public GameField(int width, int height) {
        this.width = width;
        this.height = height;
        this.grid = new CellType[width][height];
        for (CellType[] row : grid) {
            Arrays.fill(row, CellType.EMPTY);
        }
    }

    public void setCell(int x, int y, CellType type) { // тут реализована логика тора
        if (x >= width) {
            x = x - width;
        }
        if (y >= height) {
            y = y - height;
        }
        grid[x][y] = type;
    }

    public static int getTorX(int x, int width) {
        return (x >= width) ? x - width : x;
    }

    public static int getTorY(int y, int height) {
        return (y >= height) ? y - height : y;
    }
}
