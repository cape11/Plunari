package org.isogame.input;

import org.isogame.camera.CameraManager;
import org.isogame.entitiy.PlayerModel;
import org.isogame.map.Map;
import org.isogame.map.PathNode;
import org.isogame.map.AStarPathfinder;
// import org.lwjgl.system.MemoryStack; // Not strictly needed for this change unless used elsewhere

// import java.nio.DoubleBuffer; // Not strictly needed for this change
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.isogame.constants.Constants.*;

public class MouseHandler {
    private final long window;
    private final CameraManager camera;
    private final Map map;
    private final InputHandler inputHandler; // Keep this for tile selection
    private final PlayerModel player;       // Keep this for pathfinding
    private final AStarPathfinder pathfinder;

    private double lastMouseX, lastMouseY;
    // private boolean middleMousePressed = false; // OLD
    private boolean isLeftDraggingForPan = false;   // NEW: Flag for left-click drag panning
    private boolean isLeftMousePressed = false; // NEW: General flag for left mouse button state

    private static final double DRAG_THRESHOLD_SQUARED = 5*5; // Ignore small movements after click for selection (5 pixels squared)
    private double pressMouseX, pressMouseY;


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
            double currentX = xpos;
            double currentY = ypos;
            double dx = currentX - lastMouseX;
            double dy = currentY - lastMouseY;

            if (isLeftMousePressed && !isLeftDraggingForPan) {
                // Check if movement exceeds threshold to start a pan
                double dragDx = currentX - pressMouseX;
                double dragDy = currentY - pressMouseY;
                if ((dragDx * dragDx + dragDy * dragDy) > DRAG_THRESHOLD_SQUARED) {
                    System.out.println("Left mouse drag detected for panning.");
                    isLeftDraggingForPan = true; // Start panning
                }
            }

            if (isLeftDraggingForPan && camera != null) { // Check the new flag
                System.out.println("Panning with Left Mouse: dx=" + dx + ", dy=" + dy);
                float[] mapDelta = camera.screenVectorToMapVector((float) -dx, (float) -dy);
                camera.moveTargetPosition(mapDelta[0], mapDelta[1]);
            }
            lastMouseX = currentX;
            lastMouseY = currentY;
        });

        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            // Physical pixel coordinate calculations (existing code)
            int[] fbWidthPixels = new int[1]; int[] fbHeightPixels = new int[1];
            glfwGetFramebufferSize(window, fbWidthPixels, fbHeightPixels);
            int[] windowWidthPoints = new int[1]; int[] windowHeightPoints = new int[1];
            glfwGetWindowSize(window, windowWidthPoints, windowHeightPoints);
            double scaleX = (double) fbWidthPixels[0] / windowWidthPoints[0];
            double scaleY = (double) fbHeightPixels[0] / windowHeightPoints[0];
            int mouseX_physical = (int) (lastMouseX * scaleX); // Use lastMouseX/Y which are updated by cursorPosCallback
            int mouseY_physical = (int) (lastMouseY * scaleY);

            System.out.println("Mouse Button Event: button=" + button + ", action=" + action);

            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                if (action == GLFW_PRESS) {
                    isLeftMousePressed = true;
                    isLeftDraggingForPan = false; // Reset pan flag on new press
                    pressMouseX = lastMouseX;     // Record position at press
                    pressMouseY = lastMouseY;
                    System.out.println("Left Mouse Button PRESSED. Potential pan start or selection click.");
                    // DO NOT start pathfinding here yet. Wait for RELEASE to distinguish click from drag.

                } else if (action == GLFW_RELEASE) {
                    System.out.println("Left Mouse Button RELEASED.");
                    if (!isLeftDraggingForPan) { // If it wasn't a pan (i.e., it was a click)
                        System.out.println("Processing as Left CLICK for tile selection/pathing.");
                        if (camera != null && map != null && inputHandler != null && player != null && pathfinder != null) {
                            int[] accurateCoords = camera.screenToAccurateMapTile(mouseX_physical, mouseY_physical, map);
                            if (accurateCoords != null) {
                                inputHandler.setSelectedTile(accurateCoords[0], accurateCoords[1]);
                                List<PathNode> path = pathfinder.findPath(
                                        player.getTileRow(), player.getTileCol(),
                                        accurateCoords[1], accurateCoords[0], map); // A* usually row, col
                                player.setPath(path);
                                System.out.println("Path set for player to: C" + accurateCoords[0] + " R" + accurateCoords[1]);
                            } else {
                                System.out.println("No accurate tile coords found for pathing.");
                            }
                        }
                    } else {
                        System.out.println("Left mouse drag pan finished.");
                    }
                    isLeftMousePressed = false;
                    isLeftDraggingForPan = false; // Ensure pan flag is reset
                }
            } else if (button == GLFW_MOUSE_BUTTON_MIDDLE) { // Keep middle mouse logic if you want, or remove
                // middleMousePressed = (action == GLFW_PRESS); // Old logic
                if (action == GLFW_PRESS) System.out.println("Middle mouse button pressed (currently no action).");
                if (action == GLFW_RELEASE) System.out.println("Middle mouse button released.");
            }
        });

        glfwSetScrollCallback(window, (win, xoffset, yoffset) -> {
            System.out.println("Mouse Scroll Event: yoffset=" + yoffset);
            if (camera != null) {
                if (yoffset > 0) camera.adjustZoom(CAMERA_ZOOM_SPEED);
                else if (yoffset < 0) camera.adjustZoom(-CAMERA_ZOOM_SPEED);
            }
        });
    }
}