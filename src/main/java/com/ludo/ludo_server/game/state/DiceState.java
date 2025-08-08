package com.ludo.ludo_server.game.state;

import com.ludo.ludo_server.dice.Dice;
import lombok.Data;

@Data
class DiceState {
    private int die1;
    private int die2;
    private boolean isDoubleSix;

    public DiceState(Dice dice) {
        this.die1 = dice.getDie1();
        this.die2 = dice.getDie2();
        this.isDoubleSix = dice.isDoubleSix();
    }
}
