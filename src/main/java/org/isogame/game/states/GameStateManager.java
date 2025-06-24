// src/main/java/org/isogame/game/GameStateManager.java
package org.isogame.game;

import org.isogame.game.states.GameState;
import org.isogame.game.states.InGameState;
import org.isogame.game.states.MainMenuState;
import org.isogame.render.Renderer;

import java.util.HashMap;
import java.util.Map;

public class GameStateManager {

    public enum State {
        MAIN_MENU,
        IN_GAME
    }

    private final Map<State, GameState> gameStates;
    private GameState currentState;

    public GameStateManager(Game game, Renderer renderer) {
        this.gameStates = new HashMap<>();
        this.gameStates.put(State.MAIN_MENU, new MainMenuState(game, renderer));
        this.gameStates.put(State.IN_GAME, new InGameState(game, renderer));
        this.currentState = gameStates.get(State.MAIN_MENU);
        this.currentState.enter();
    }

    public void setState(State newState) {
        if (currentState != null) {
            currentState.exit();
        }
        currentState = gameStates.get(newState);
        currentState.enter();
    }

    public void update(double deltaTime) {
        if (currentState != null) {
            currentState.update(deltaTime);
        }
    }

    public void render(double deltaTime) {
        if (currentState != null) {
            currentState.render(deltaTime);
        }
    }

    public GameState getCurrentState() {
        return currentState;
    }
}