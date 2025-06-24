// src/main/java/org/isogame/game/states/GameState.java
package org.isogame.game.states;

public interface GameState {
    void enter();
    void update(double deltaTime);
    void render(double deltaTime);
    void exit();
}