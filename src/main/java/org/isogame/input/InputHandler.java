package org.isogame.input;

import org.isogame.camera.CameraManager;
import static org.lwjgl.glfw.GLFW.*;

public class InputHandler {

    private final long window;
    private final int[][] alturas;
    private final int ALTURA_MAXIMA;
    private final int MAP_WIDTH, MAP_HEIGHT;
    private final CameraManager cameraManager; // Added camera reference

    // Character position variables
    public int characterRow = 0, characterCol = 0;

    // We'll keep these for compatibility with existing code
    public int currentRow = 0, currentCol = 0;

    public boolean levitating = false;
    public float levitateTimer = 0;

    // Updated constructor to include camera manager
    public InputHandler(long window, int[][] alturas, int alturaMaxima, int mapWidth, int mapHeight, CameraManager cameraManager) {
        this.window = window;
        this.alturas = alturas;
        this.ALTURA_MAXIMA = alturaMaxima;
        this.MAP_WIDTH = mapWidth;
        this.MAP_HEIGHT = mapHeight;
        this.cameraManager = cameraManager;

        // Initialize character position to center of map
        this.characterRow = mapHeight / 2;
        this.characterCol = mapWidth / 2;

        // Set current position to character position
        this.currentRow = this.characterRow;
        this.currentCol = this.characterCol;
    }

    public void registerCallbacks(Runnable onGenerateMap) {
        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE)
                glfwSetWindowShouldClose(window, true);

            if (action == GLFW_PRESS || action == GLFW_REPEAT) {
                switch (key) {
                    // Arrow keys now move the character instead of selecting tiles
                    case GLFW_KEY_UP:
                        characterRow = Math.max(0, characterRow - 1);
                        // Update current position to follow character
                        currentRow = characterRow;
                        currentCol = characterCol;
                        break;
                    case GLFW_KEY_DOWN:
                        characterRow = Math.min(MAP_HEIGHT - 1, characterRow + 1);
                        // Update current position to follow character
                        currentRow = characterRow;
                        currentCol = characterCol;
                        break;
                    case GLFW_KEY_LEFT:
                        characterCol = Math.max(0, characterCol - 1);
                        // Update current position to follow character
                        currentRow = characterRow;
                        currentCol = characterCol;
                        break;
                    case GLFW_KEY_RIGHT:
                        characterCol = Math.min(MAP_WIDTH - 1, characterCol + 1);
                        // Update current position to follow character
                        currentRow = characterRow;
                        currentCol = characterCol;
                        break;
                    // Camera controls with WASD keys
                    case GLFW_KEY_W:
                        cameraManager.moveCamera(0, -0.5f);
                        break;
                    case GLFW_KEY_S:
                        cameraManager.moveCamera(0, 0.5f);
                        break;
                    case GLFW_KEY_A:
                        cameraManager.moveCamera(-0.5f, 0);
                        break;
                    case GLFW_KEY_D:
                        cameraManager.moveCamera(0.5f, 0);
                        break;
                    // Height adjustment keys changed to Q and E to avoid conflict with WASD
                    case GLFW_KEY_Q:
                        alturas[currentRow][currentCol] = Math.min(ALTURA_MAXIMA, alturas[currentRow][currentCol] + 1);
                        break;
                    case GLFW_KEY_E:
                        alturas[currentRow][currentCol] = Math.max(0, alturas[currentRow][currentCol] - 1);
                        break;
                    case GLFW_KEY_G:
                        if (onGenerateMap != null) onGenerateMap.run();
                        break;
                    case GLFW_KEY_F:
                        levitating = !levitating;
                        levitateTimer = 0;
                        break;
                    // Center camera on character with C key
                    case GLFW_KEY_C:
                        cameraManager.setTargetPosition(characterCol, characterRow);
                        break;
                }
            }
        });
    }

    // Method to update game state based on input
    public void update() {
        // You could add more continuous input handling here if needed
    }
}