// src/main/java/org/isogame/game/states/InGameState.java
package org.isogame.game.states;

import org.isogame.game.Game;
import org.isogame.render.Renderer;

import static org.lwjgl.opengl.GL11C.*;

public class InGameState implements GameState {

    private final Game game;
    private final Renderer renderer;

    public InGameState(Game game, Renderer renderer) {
        this.game = game;
        this.renderer = renderer;
    }

    @Override
    public void enter() {
        // Logic to run when entering the in-game state (e.g., ensuring the world is loaded)
    }

    @Override
    public void update(double deltaTime) {
        game.updateGameLogic(deltaTime);
    }


    @Override
    public void render(double deltaTime) {
        // --- 3D World Rendering ---
        glEnable(GL_DEPTH_TEST);
        renderer.render(game.getPseudoTimeOfDay(), deltaTime);

        // --- 2D UI Rendering ---
        glDisable(GL_DEPTH_TEST);
        // This single call to the UIManager will now handle everything.
        game.getUiManager().render();
    }

    @Override
    public void exit() {
        if (game.getCurrentWorldName() != null && !game.getCurrentWorldName().trim().isEmpty()) {
            game.saveGame(game.getCurrentWorldName());
        }
    }
}