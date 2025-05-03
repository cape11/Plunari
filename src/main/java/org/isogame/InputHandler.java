package org.isogame.inputhandler;

import static org.lwjgl.glfw.GLFW.*;

public class InputHandler {

    private final long window;
    private final int[][] alturas;
    private final int ALTURA_MAXIMA;
    private final int MAP_WIDTH, MAP_HEIGHT;

    public int currentRow = 0, currentCol = 0;
    public boolean levitating = false;
    public float levitateTimer = 0;

    public InputHandler(long window, int[][] alturas, int alturaMaxima, int mapWidth, int mapHeight) {
        this.window = window;
        this.alturas = alturas;
        this.ALTURA_MAXIMA = alturaMaxima;
        this.MAP_WIDTH = mapWidth;
        this.MAP_HEIGHT = mapHeight;
    }

    public void registerCallbacks(Runnable onGenerateMap) {
        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE)
                glfwSetWindowShouldClose(window, true);

            if (action == GLFW_PRESS || action == GLFW_REPEAT) {
                switch (key) {
                    case GLFW_KEY_UP:
                        currentRow = Math.max(0, currentRow - 1);
                        break;
                    case GLFW_KEY_DOWN:
                        currentRow = Math.min(MAP_HEIGHT - 1, currentRow + 1);
                        break;
                    case GLFW_KEY_LEFT:
                        currentCol = Math.max(0, currentCol - 1);
                        break;
                    case GLFW_KEY_RIGHT:
                        currentCol = Math.min(MAP_WIDTH - 1, currentCol + 1);
                        break;
                    case GLFW_KEY_A:
                        alturas[currentRow][currentCol] = Math.min(ALTURA_MAXIMA, alturas[currentRow][currentCol] + 1);
                        break;
                    case GLFW_KEY_Z:
                        alturas[currentRow][currentCol] = Math.max(0, alturas[currentRow][currentCol] - 1);
                        break;
                    case GLFW_KEY_G:
                        if (onGenerateMap != null) onGenerateMap.run();
                        break;
                    case GLFW_KEY_S:
                        levitating = !levitating;
                        levitateTimer = 0;
                        break;
                }
            }
        });
    }
}
