// src/main/java/org/isogame/game/states/MainMenuState.java
package org.isogame.game.states;

import org.isogame.game.Game;
import org.isogame.render.Renderer;
import org.isogame.ui.MenuItemButton;
import java.util.List;

import static org.lwjgl.opengl.GL11C.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11C.glDisable;

public class MainMenuState implements GameState {

    private final Game game;
    private final Renderer renderer;

    public MainMenuState(Game game, Renderer renderer) {
        this.game = game;
        this.renderer = renderer;
    }

    @Override
    public void enter() {
        game.refreshAvailableSaveFiles();
        renderer.clearGameContext();
    }

    @Override
    public void update(double deltaTime) {
        // The MouseHandler already updates button hover states
    }

    @Override
    public void render(double deltaTime) {
        // The main menu is 2D, so we disable the depth test.
        glDisable(GL_DEPTH_TEST);
        game.getUiManager().renderMainMenu();
    }

    @Override
    public void exit() {
        // No special cleanup needed for the main menu
    }
}