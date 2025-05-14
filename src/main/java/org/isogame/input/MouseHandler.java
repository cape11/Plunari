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
        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            captureMousePosition();

            if (button == GLFW_MOUSE_BUTTON_MIDDLE) {
                middleMousePressed = (action == GLFW_PRESS);
            } else if (button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_PRESS) {
                // Use the accurate picking for pathfinding target
                int[] accurateCoordsPath = camera.screenToAccurateMapTile((int) lastMouseX, (int) lastMouseY, map);
                handlePathfindingRequest(accurateCoordsPath[0], accurateCoordsPath[1]); // Pass col, row

                // Also update the selected tile for other UI/interactions using accurate picking
                int[] accurateCoordsSelect = camera.screenToAccurateMapTile((int) lastMouseX, (int) lastMouseY, map);
                inputHandler.setSelectedTile(accurateCoordsSelect[0], accurateCoordsSelect[1]); // Pass col, row
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