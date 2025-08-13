package com.ludo.ludo_server.board;



/**
 * Position record - Represents a coordinate (row, col) on the Ludo board.
 * 'row' corresponds to the vertical axis (y-axis in Cartesian, but first index in 2D array).
 * 'col' corresponds to the horizontal axis (x-axis in Cartesian, but second index in 2D array).
 */
public record Position(int row, int col) {

    @Override
    public String toString() {
        return "(" + row + ", " + col + ")";
    }
}