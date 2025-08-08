package com.ludo.ludo_server.board;

import java.util.Objects;

/**
 * Position class - Represents a coordinate (row, col) on the Ludo board.
 * 'row' corresponds to the vertical axis (y-axis in Cartesian, but first index in 2D array).
 * 'col' corresponds to the horizontal axis (x-axis in Cartesian, but second index in 2D array).
 */
public class Position {
    private final int row;
    private final int col;

    public Position(int row, int col) {
        this.row = row;
        this.col = col;
    }

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Position position = (Position) obj;
        return row == position.row && col == position.col;
    }

    @Override
    public int hashCode() {
        return Objects.hash(row, col); // Use Objects.hash for better hash code
    }

    @Override
    public String toString() {
        return "(" + row + ", " + col + ")";
    }
}
