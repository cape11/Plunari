package org.isogame.input;

import org.isogame.camera.CameraManager;
import org.isogame.map.Map;
import org.lwjgl.system.MemoryStack;
import java.nio.DoubleBuffer;
import static org.lwjgl.glfw.GLFW.*;
import static org.isogame.constants.Constants.*;

public class MouseHandler {
    private final long window;
    private final CameraManager camera;
    private final Map map; // Need map reference for bounds checking
    private final InputHandler inputHandler; // To set selected tile

    private double lastMouseX, lastMouseY;
    private boolean middleMousePressed = false; // For panning
    private boolean leftMousePressed = false;   // For selection dragging

    public MouseHandler(long window, CameraManager camera, Map map, InputHandler inputHandler) {
        this.window = window;
        this.camera = camera;
        this.map = map;
        this.inputHandler = inputHandler;

        setupCallbacks();
    }

    private void setupCallbacks() {
        // Mouse button callback
        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            captureMousePosition(); // Update position on any click action

            if (button == GLFW_MOUSE_BUTTON_MIDDLE) {
                middleMousePressed = (action == GLFW_PRESS);
            } else if (button == GLFW_MOUSE_BUTTON_LEFT) {
                leftMousePressed = (action == GLFW_PRESS);
                if (leftMousePressed) {
                    handleTileSelection(lastMouseX, lastMouseY); // Select on initial press
                }
            }
            // Add right mouse button handling if needed
        });

        // Mouse position callback (Cursor Movement)
        glfwSetCursorPosCallback(window, (win, xpos, ypos) -> {
            double currentX = xpos;
            double currentY = ypos;

            if (middleMousePressed) {
                // Handle camera panning based on delta movement
                double deltaX = currentX - lastMouseX;
                double deltaY = currentY - lastMouseY;

                // Convert screen delta to map delta (adjust sensitivity as needed)
                // Panning should feel intuitive, so map moves opposite to mouse drag
                float screenMoveScale = 1.0f / camera.getZoom(); // Panning speed depends on zoom
                float[] mapDelta = camera.screenVectorToMapVector((float) -deltaX * screenMoveScale, (float) -deltaY * screenMoveScale);

                camera.moveTargetPosition(mapDelta[0], mapDelta[1]); // Use moveTargetPosition for smooth panning
            } else if (leftMousePressed) {
                // Continuously update selected tile while dragging
                handleTileSelection(currentX, currentY);
            }

            // Update last known position for the next callback
            lastMouseX = currentX;
            lastMouseY = currentY;
        });

        // Scroll callback for zoom
        glfwSetScrollCallback(window, (win, xoffset, yoffset) -> {
            if (yoffset > 0) {
                camera.adjustZoom(CAMERA_ZOOM_SPEED);  // Zoom in
            } else if (yoffset < 0) {
                camera.adjustZoom(-CAMERA_ZOOM_SPEED); // Zoom out
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

    // Handles converting screen coords to map tile and updating InputHandler
    private void handleTileSelection(double screenX, double screenY) {
        // Convert screen coordinates to map coordinates (can be fractional)
        float[] mapCoords = camera.screenToMapCoords((int) screenX, (int) screenY);

        // Find the closest tile CENTER (more intuitive for isometric selection)
        // This requires a slightly more complex calculation than simple rounding
        // For now, stick to rounding, but be aware it might select adjacent tiles sometimes.
        int col = Math.round(mapCoords[0]);
        int row = Math.round(mapCoords[1]);

        // Update the selected tile in the InputHandler if it's valid
        if (map.isValid(row, col)) {
            inputHandler.setSelectedTile(row, col);
        }
    }

    // This update method is not needed if logic is handled in callbacks
    // public void update(double deltaTime) {}
}