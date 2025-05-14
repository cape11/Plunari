package org.isogame.input;

import org.isogame.camera.CameraManager;
import org.isogame.entitiy.PlayerModel;
import org.isogame.map.Map;
import org.isogame.map.PathNode;
import org.isogame.map.AStarPathfinder;
import org.lwjgl.system.MemoryStack;

import java.nio.DoubleBuffer;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.isogame.constants.Constants.*;

public class MouseHandler {
    private final long window;
    private final CameraManager camera;
    private final Map map;
    private final InputHandler inputHandler;
    private final PlayerModel player;
    private final AStarPathfinder pathfinder;

    private double lastMouseX, lastMouseY;
    private boolean middleMousePressed = false;

    public MouseHandler(long window, CameraManager camera, Map map, InputHandler inputHandler, PlayerModel player) {
        this.window = window;
        this.camera = camera;
        this.map = map;
        this.inputHandler = inputHandler;
        this.player = player;
        this.pathfinder = new AStarPathfinder();
        setupCallbacks();
    }

    private void setupCallbacks() {
        glfwSetCursorPosCallback(window, (win, xpos, ypos) -> {
            lastMouseX = xpos; // These are logical screen coordinates
            lastMouseY = ypos;
        });

        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            // captureMousePosition(); // Already have lastMouseX/Y from cursorPosCallback

            if (button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_PRESS) {
                // Get current window size (logical points) and framebuffer size (physical pixels)
                // This is crucial for scaling mouse coordinates correctly.
                int[] windowWidthPoints = new int[1];
                int[] windowHeightPoints = new int[1];
                glfwGetWindowSize(window, windowWidthPoints, windowHeightPoints);

                int[] fbWidthPixels = new int[1];
                int[] fbHeightPixels = new int[1];
                glfwGetFramebufferSize(window, fbWidthPixels, fbHeightPixels);

                double mouseX_logical = lastMouseX;
                double mouseY_logical = lastMouseY;

                // Calculate scale factors
                double scaleX = (double) fbWidthPixels[0] / windowWidthPoints[0];
                double scaleY = (double) fbHeightPixels[0] / windowHeightPoints[0];
                // On macOS Retina, scaleX and scaleY will typically be 2.0

                // Scale mouse coordinates to framebuffer pixel space
                int mouseX_physical = (int) (mouseX_logical * scaleX);
                int mouseY_physical = (int) (mouseY_logical * scaleY);

                System.out.printf("Mouse: Logical(%.0f,%.0f) -> Scaled Physical(X:%d, Y:%d) (ScaleX:%.1f, ScaleY:%.1f)%n",
                        mouseX_logical, mouseY_logical, mouseX_physical, mouseY_physical, scaleX, scaleY);

                // Use the SCALED physical coordinates for picking
                int[] accurateCoordsPath = camera.screenToAccurateMapTile(mouseX_physical, mouseY_physical, map);

                // ----- !!! CRUCIAL NULL CHECK !!! -----
                if (accurateCoordsPath != null) {
                    int targetColPath = accurateCoordsPath[0];
                    int targetRowPath = accurateCoordsPath[1];

                    inputHandler.setSelectedTile(targetColPath, targetRowPath);

                    System.out.printf("Pathfinding request from (R:%d,C:%d) to (R:%d,C:%d)%n",
                            player.getTileRow(), player.getTileCol(),
                            targetRowPath, targetColPath);

                    List<PathNode> path = pathfinder.findPath(
                            player.getTileRow(), player.getTileCol(),
                            targetRowPath, targetColPath,
                            map
                    );
                    player.setPath(path);

                } else {
                    // screenToAccurateMapTile returned null
                    System.out.println("Mouse click for pathfinding/selection DID NOT LAND on a valid map tile according to screenToAccurateMapTile.");
                    // Optionally, clear selection or do nothing:
                    // inputHandler.setSelectedTile(-1, -1); // Example: deselect if you want this behavior
                }
            }
        });

        glfwSetCursorPosCallback(window, (win, xpos, ypos) -> {
            if (middleMousePressed) {
                double deltaX = xpos - lastMouseX;
                double deltaY = ypos - lastMouseY;
                float screenMoveScale = 1.0f / camera.getZoom();
                float[] mapDelta = camera.screenVectorToMapVector((float) -deltaX * screenMoveScale, (float) -deltaY * screenMoveScale);
                camera.moveTargetPosition(mapDelta[0], mapDelta[1]);
            }
            // Update the "hovered" tile for visual feedback (optional, done in InputHandler or Renderer)
            // if (inputHandler != null) {
            //    int[] hoveredCoords = camera.screenToAccurateMapTile((int) xpos, (int) ypos, map);
            //    inputHandler.setHoveredTile(hoveredCoords[0], hoveredCoords[1]); // Would need this method in InputHandler
            // }
            lastMouseX = xpos;
            lastMouseY = ypos;
        });
// In MouseHandler.java -> setupCallbacks() -> glfwSetMouseButtonCallback for LEFT_PRESS

// This call returns the coordinates for pathfinding
        int[] accurateCoordsPath = camera.screenToAccurateMapTile((int) lastMouseX, (int) lastMouseY, map);

        if (accurateCoordsPath != null) { // <<< ADD THIS NULL CHECK
            int targetColPath = accurateCoordsPath[0]; // Column is usually index 0
            int targetRowPath = accurateCoordsPath[1]; // Row is usually index 1

            System.out.printf("Pathfinding request from (R:%d,C:%d) to (R:%d,C:%d)%n",
                    player.getTileRow(), player.getTileCol(),
                    targetRowPath, targetColPath);

            List<PathNode> path = pathfinder.findPath(
                    player.getTileRow(),    // startRow
                    player.getTileCol(),    // startCol
                    targetRowPath,          // endRow
                    targetColPath,          // endCol
                    map                     // <<< ADD THE 'map' INSTANCE HERE
            );
            player.setPath(path);

        } else {
            System.out.println("Mouse click for pathfinding did not land on a valid map tile.");
            // Optionally, clear the player's path if they click off-map while trying to set a new path
            // player.setPath(null);
        }

// This part is for setting the selected tile in InputHandler (visual selection)
// You can reuse accurateCoordsPath if it was not null, or call again if you want to be super safe
// or if the logic could diverge (though it shouldn't here).
// For simplicity, let's assume if pathing target is valid, selection target is too.
        if (accurateCoordsPath != null) {
            inputHandler.setSelectedTile(accurateCoordsPath[0], accurateCoordsPath[1]); // col, row
        } else {
            // If no valid tile for pathfinding, maybe don't change selection or set to invalid
            // inputHandler.setSelectedTile(-1, -1); // Example for invalid/no selection
        }

        glfwSetScrollCallback(window, (win, xoffset, yoffset) -> {
            if (yoffset > 0) {
                camera.adjustZoom(CAMERA_ZOOM_SPEED);
            } else if (yoffset < 0) {
                camera.adjustZoom(-CAMERA_ZOOM_SPEED);
            }
        });
    }

    // Renamed parameters to reflect they are already map coordinates
    private void handlePathfindingRequest(int targetCol, int targetRow) {
        // targetCol and targetRow are now already accurately picked map coordinates

        if (map.isValid(targetRow, targetCol)) {
            if (player.getTileRow() == targetRow && player.getTileCol() == targetCol) {
                System.out.println("Target is player's current tile. No path needed.");
                player.setPath(null);
                return;
            }

            System.out.println("Pathfinding request from (" + player.getTileRow() + "," + player.getTileCol() +
                    ") to (" + targetRow + "," + targetCol + ")");

            List<PathNode> path = pathfinder.findPath(
                    player.getTileRow(), player.getTileCol(),
                    targetRow, targetCol,
                    map
            );
            player.setPath(path);
        }
    }

    // These methods are no longer strictly needed if the logic is moved directly into the callbacks,
    // but if InputHandler still uses them, they should use the accurate picking.
    // Let's remove them for now to avoid confusion, as the callbacks now directly call screenToAccurateMapTile.
    /*
    private int getTileRowAtMouse(double screenX, double screenY) {
        int[] accurateCoords = camera.screenToAccurateMapTile((int) screenX, (int) screenY, map);
        return accurateCoords[1]; // row
    }

    private int getTileColAtMouse(double screenX, double screenY) {
        int[] accurateCoords = camera.screenToAccurateMapTile((int) screenX, (int) screenY, map);
        return accurateCoords[0]; // col
    }
    */

    private void captureMousePosition() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            DoubleBuffer posX = stack.mallocDouble(1);
            DoubleBuffer posY = stack.mallocDouble(1);
            glfwGetCursorPos(window, posX, posY);
            lastMouseX = posX.get(0);
            lastMouseY = posY.get(0);
        }
    }
}