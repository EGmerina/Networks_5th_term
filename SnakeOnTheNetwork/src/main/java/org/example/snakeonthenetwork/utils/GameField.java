package org.example.snakeonthenetwork.utils;

public class GameField {
    private final int width;
    private final int height;
    private final int[][] grid;

    public GameField(int width, int height) {
        this.width = width;
        this.height = height;
        this.grid = new int[width][height];
    }
}
