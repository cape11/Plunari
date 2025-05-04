package org.isogame.input;

import org.isogame.camera.CameraManager;
import org.lwjgl.system.MemoryStack;

import java.nio.DoubleBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.isogame.constants.Constants.*;

public class MouseHandler {
    private final long window;
    private final CameraManager camera;
    private final InputHandler inputHandler;
    private double lastMouseX, lastMouseY;
    private boolean middleMousePressed = false;
    private boolean leftMousePressed = false;

    public MouseHandler(long window, CameraManager camera, InputHandler inputHandler) {
        this.window = window;
        this.camera = camera;
        this.inputHandler = inputHandler;

        setupCallbacks();
    }

    private void setupCallbacks() {
        // Mouse button callback
        glfwSetMouseButtonCallback(window, (window, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_MIDDLE) {
                // Middle mouse for camera panning
                if (action == GLFW_PRESS) {
                    middleMousePressed = true;
                    captureMousePosition();
                } else if (action == GLFW_RELEASE) {
                    middleMousePressed = false;
                }
            } else if (button == GLFW_MOUSE_BUTTON_LEFT) {
                // Left mouse button for tile selection
                if (action == GLFW_PRESS) {
                    leftMousePressed = true;
                    handleTileSelection();
                } else if (action == GLFW_RELEASE) {
                    leftMousePressed = false;
                }
            }
        });

        // Mouse position callback
        glfwSetCursorPosCallback(window, (window, xpos, ypos) -> {
            if (middleMousePressed) {
                // Handle camera panning
                double dx = xpos - lastMouseX;
                double dy = ypos - lastMouseY;

                // Convert screen movement to isometric map movement
                float moveScaleX = 1.0f / (TILE_WIDTH * camera.getZoom());
                float moveScaleY = 1.0f / (TILE_HEIGHT * camera.getZoom());

                // Updated isometric conversion for better accuracy
                float mapDx = (float)((dx * 2 - dy) * moveScaleX * 0.25f);
                float mapDy = (float)((dx + dy * 2) * moveScaleY * 0.25f);

                // Move camera in opposite direction of drag
                camera.moveCamera(-mapDx, -mapDy);
            } else if (leftMousePressed) {
                // Continuously update selected tile when dragging with left mouse
                handleTileSelection();
            }

            // Update last position
            lastMouseX = xpos;
            lastMouseY = ypos;
        });

        // Scroll callback for zoom
        glfwSetScrollCallback(window, (window, xoffset, yoffset) -> {
            if (yoffset > 0) {
                camera.adjustZoom(0.1f);  // Zoom in
            } else if (yoffset < 0) {
                camera.adjustZoom(-0.1f); // Zoom out
            }
        });
    }

    private void captureMousePosition() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            DoubleBuffer posX = stack.mallocDouble(1);
            DoubleBuffer posY = stack.mallocDouble(1);
            glfwGetCursorPos(window, posX, posY);
            lastMouseX = posX.get(0);
            lastMouseY = posY.get(0);
        }
    }

    // Re-enabled tile selection for left mouse clicks
    private void handleTileSelection() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            DoubleBuffer posX = stack.mallocDouble(1);
            DoubleBuffer posY = stack.mallocDouble(1);
            glfwGetCursorPos(window, posX, posY);

            // Convert screen coordinates to map coordinates
            float[] mapCoords = camera.screenToMapCoords((int)posX.get(0), (int)posY.get(0));

            // Round to nearest tile
            int col = Math.round(mapCoords[0]);
            int row = Math.round(mapCoords[1]);

            // Ensure within map bounds
            if (row >= 0 && row < MAP_HEIGHT && col >= 0 && col < MAP_WIDTH) {
                // Update both current position AND character position
                inputHandler.currentRow = row;
                inputHandler.currentCol = col;
                inputHandler.characterRow = row;
                inputHandler.characterCol = col;
            }
        }
    }

    // Method to handle continuous updates (call this in the game loop if needed)
    public void update() {
        // No continuous updates needed here anymore,
        // as we handle tile selection in the callback
    }
}