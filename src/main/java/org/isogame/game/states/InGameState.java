// src/main/java/org/isogame/game/states/InGameState.java
package org.isogame.game.states;

import org.isogame.game.Game;
import org.isogame.game.GameStateManager;
import org.isogame.render.Renderer;
import org.isogame.world.World;

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
        // No changes needed here. Logic is handled by Game when creating/loading a world.
    }

    @Override
    public void update(double deltaTime) {
        // Get the current world from the Game class each frame.
        World currentWorld = game.getWorld();
        if (currentWorld == null) {
            // Failsafe: If there's no world, we shouldn't be in this state.
            game.getGameStateManager().setState(GameStateManager.State.MAIN_MENU);
            return;
        }

        // --- THE FIX IS HERE ---
        // 1. Handle all player input first, passing deltaTime to the input handler.

        // 2. Now, update the entire world simulation based on the input.
        currentWorld.update(deltaTime);

        // 3. Finally, update the camera to follow the player's new position.
        if (game.getRenderer() != null && game.getRenderer().getCamera() != null) {
            game.getRenderer().getCamera().update(deltaTime);
        }
    }


    @Override
    public void render(double deltaTime) {
        glEnable(GL_DEPTH_TEST);

        // *** FIX: This call is now correct because Renderer.render takes a World object ***
        World currentWorld = game.getWorld();
        if (currentWorld != null) {
            renderer.render(currentWorld, deltaTime);
        }

        glDisable(GL_DEPTH_TEST);
        game.getUiManager().render();
    }

    @Override
    public void exit() {
        // Save the game when exiting to the main menu
        if (game.getCurrentWorldName() != null && !game.getCurrentWorldName().trim().isEmpty()) {
            game.saveGame(game.getCurrentWorldName());
        }
    }
}