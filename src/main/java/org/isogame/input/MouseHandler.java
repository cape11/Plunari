// In MouseHandler.java
package org.isogame.input;

import org.isogame.camera.CameraManager;
import org.isogame.entitiy.PlayerModel;
import org.isogame.game.Game; // Import Game
import org.isogame.inventory.InventorySlot; // Import InventorySlot
import org.isogame.item.Item;
import org.isogame.map.Map;
import org.isogame.map.PathNode;
import org.isogame.map.AStarPathfinder;

import java.util.List; // Import List

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
    private boolean isLeftDraggingForPan = false;
    private boolean isLeftMousePressed = false;
    private double pressMouseX, pressMouseY;
    private static final double DRAG_THRESHOLD_SQUARED = 5*5;

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
                double dragDx = currentX - pressMouseX;
                double dragDy = currentY - pressMouseY;
                if ((dragDx * dragDx + dragDy * dragDy) > DRAG_THRESHOLD_SQUARED) {
                    isLeftDraggingForPan = true;
                }
            }

            if (isLeftDraggingForPan && camera != null) {
                float[] mapDelta = camera.screenVectorToMapVector((float) -dx, (float) -dy);
                camera.moveTargetPosition(mapDelta[0], mapDelta[1]);
            }
            lastMouseX = currentX;
            lastMouseY = currentY;
        });

        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            // Calculate physical pixel coordinates for every mouse button event
            int[] fbWidthPixels = new int[1];
            int[] fbHeightPixels = new int[1];
            glfwGetFramebufferSize(window, fbWidthPixels, fbHeightPixels);
            int[] windowWidthPoints = new int[1];
            int[] windowHeightPoints = new int[1];
            glfwGetWindowSize(window, windowWidthPoints, windowHeightPoints);
            double scaleX = (double) fbWidthPixels[0] / windowWidthPoints[0];
            double scaleY = (double) fbHeightPixels[0] / windowHeightPoints[0];
            int mouseX_physical = (int) (lastMouseX * scaleX); // Use lastMouseX/Y
            int mouseY_physical = (int) (lastMouseY * scaleY); // Use lastMouseX/Y

            Game game = null;
            if (inputHandler != null) { // Ensure inputHandler is not null before trying to get game instance
                game = inputHandler.getGameInstance();
            }


            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                if (action == GLFW_PRESS) {
                    isLeftMousePressed = true;
                    isLeftDraggingForPan = false;
                    pressMouseX = lastMouseX;
                    pressMouseY = lastMouseY;

                    // --- Inventory Slot Click Detection on Left Press ---
                    if (game != null && game.isInventoryVisible()) {
                        // Calculate panel dimensions and slot positions (consistent with Renderer.renderInventoryUI)
                        float panelPadding = 20f;
                        int slotsPerRow = 5; // Match PlayerModel or Renderer definition
                        float slotSize = 50f;
                        float slotMargin = 10f;

                        List<InventorySlot> slots = player.getInventorySlots();
                        int numRows = (slots.isEmpty()) ? 1 : (int) Math.ceil((double) slots.size() / slotsPerRow);

                        float panelWidth = (slotsPerRow * slotSize) + ((slotsPerRow + 1) * slotMargin);
                        float panelHeight = (numRows * slotSize) + ((numRows + 1) * slotMargin);
                        float panelX = (camera.getScreenWidth() - panelWidth) / 2.0f;
                        float panelY = (camera.getScreenHeight() - panelHeight) / 2.0f;

                        float currentSlotX = panelX + slotMargin;
                        float currentSlotY = panelY + slotMargin;
                        int colCount = 0;
                        boolean clickHandledByInventory = false;

                        for (int i = 0; i < slots.size(); i++) {
                            if (mouseX_physical >= currentSlotX && mouseX_physical <= currentSlotX + slotSize &&
                                    mouseY_physical >= currentSlotY && mouseY_physical <= currentSlotY + slotSize) {

                                game.setSelectedInventorySlotIndex(i); // This is likely from PlayerModel now
                                // player.setSelectedHotbarSlotIndex(i); // If you moved selection to PlayerModel
                                System.out.println("Clicked inventory slot index: " + i);
                                clickHandledByInventory = true;
                                // Set a flag to prevent world interaction on release if this click was on UI
                                // For example, in MouseHandler: private boolean uiClickJustOccurred = false;
                                // uiClickJustOccurred = true;
                                break;
                            }
                            currentSlotX += slotSize + slotMargin;
                            colCount++;
                            if (colCount >= slotsPerRow) {
                                colCount = 0;
                                currentSlotX = panelX + slotMargin;
                                currentSlotY += slotSize + slotMargin;
                            }
                        }
                        if (clickHandledByInventory) {
                            isLeftDraggingForPan = true; // Prevent pathing on release by faking a drag
                        } else if (game.isInventoryVisible()){ // Click was not on a slot, but inventory is open
                            game.setSelectedInventorySlotIndex(-1); // Deselect slot
                            // player.setSelectedHotbarSlotIndex(-1);
                        }
                    }
                } else if (action == GLFW_RELEASE) {
                    if (!isLeftDraggingForPan) { // Was a click, not a drag
                        if (game != null && game.isInventoryVisible() && player.getSelectedHotbarSlotIndex() != -1) {
                            // If inventory is open and a slot is selected, left click might be for UI interaction (future)
                            // For now, do nothing specific, just prevent pathing.
                        } else if (camera != null && map != null && inputHandler != null && player != null && pathfinder != null) {
                            // Process tile selection / pathing only if UI didn't handle it
                            int[] accurateCoords = camera.screenToAccurateMapTile(mouseX_physical, mouseY_physical, map);
                            if (accurateCoords != null) {
                                inputHandler.setSelectedTile(accurateCoords[0], accurateCoords[1]);
                                List<PathNode> path = pathfinder.findPath(
                                        player.getTileRow(), player.getTileCol(),
                                        accurateCoords[1], accurateCoords[0], map);
                                player.setPath(path);
                            }
                        }
                    }
                    isLeftMousePressed = false;
                    isLeftDraggingForPan = false;
                }
            } else if (button == GLFW_MOUSE_BUTTON_RIGHT && action == GLFW_RELEASE) {
                // Block Placement Logic
                Item itemToPlace = player.getSelectedItemForPlacement();
                if (itemToPlace != null) {
                    int[] targetCoords = camera.screenToAccurateMapTile(mouseX_physical, mouseY_physical, map); // Use mouseX_physical, mouseY_physical here

                    if (targetCoords != null) {
                        int targetC = targetCoords[0];
                        int targetR = targetCoords[1];

                        int playerR = player.getTileRow();
                        int playerC = player.getTileCol();
                        int distance = Math.abs(targetR - playerR) + Math.abs(targetC - playerC);
                        // Allow placement within MAX_INTERACTION_DISTANCE +1 for easier building
                        if (distance <= MAX_INTERACTION_DISTANCE + 1) {
                            if (map.placeBlock(targetR, targetC, itemToPlace)) {
                                if (!player.consumeSelectedItemForPlacement(1)) {
                                    System.err.println("Placed block but failed to consume item. Reverting (TODO)");
                                    // TODO: Add logic to revert map.placeBlock if consumption fails
                                }
                            }
                        } else {
                            System.out.println("Target tile for placement is too far away.");
                        }
                    }
                }
            }
        });

        glfwSetScrollCallback(window, (win, xoffset, yoffset) -> {
            if (camera != null) {
                if (yoffset > 0) camera.adjustZoom(CAMERA_ZOOM_SPEED);
                else if (yoffset < 0) camera.adjustZoom(-CAMERA_ZOOM_SPEED);
            }
        });
    }
}