package org.isogame.input;

import org.isogame.camera.CameraManager;
import org.isogame.constants.Constants;
import org.isogame.entitiy.PlayerModel;
import org.isogame.game.Game;
import org.isogame.inventory.InventorySlot;
import org.isogame.item.Item;
import org.isogame.map.Map;
import org.isogame.ui.MenuItemButton;

import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.isogame.constants.Constants.*;

public class MouseHandler {
    private final long window;
    private final CameraManager camera;
    private Map map;
    private InputHandler inputHandlerRef;
    private PlayerModel player;
    private final Game gameInstance;

    private double lastMouseX, lastMouseY;
    private boolean isLeftDraggingForPan = false;
    private boolean isLeftMousePressed = false;
    private double pressMouseX, pressMouseY;
    private static final double DRAG_THRESHOLD_SQUARED = 5 * 5;
    private boolean uiHandledLeftMousePress = false;

    public MouseHandler(long window, CameraManager camera, Map map, InputHandler inputHandler, PlayerModel player, Game gameInstance) {
        this.window = window;
        this.camera = camera; // CameraManager should always be valid
        this.map = map; // Can be null initially
        this.inputHandlerRef = inputHandler; // Can be null initially
        this.player = player; // Can be null initially
        this.gameInstance = gameInstance; // Should always be valid
        setupCallbacks();
        System.out.println("MouseHandler: Initialized. GameInstance: " + (this.gameInstance != null) +
                ", Camera: " + (this.camera != null) +
                ", Map: " + (this.map != null) +
                ", InputHandler: " + (this.inputHandlerRef != null) +
                ", Player: " + (this.player != null));
    }

    /**
     * Updates the internal map, player, and input handler references.
     * Called when the game starts or loads a new world, or returns to menu.
     * @param map The current game map (can be null for menu).
     * @param player The current player model (can be null for menu).
     * @param inputHandler The current input handler (can be null if clearing context for menu, though usually kept).
     */
    public void updateGameReferences(Map map, PlayerModel player, InputHandler inputHandler) {
        this.map = map;
        this.player = player;
        this.inputHandlerRef = inputHandler; // Update this reference as InputHandler might be re-created or updated
        System.out.println("MouseHandler: Updated game references. Map: " + (this.map != null) +
                ", Player: " + (this.player != null) +
                ", InputHandler: " + (this.inputHandlerRef != null));
    }


    public void resetLeftMouseDragFlags() {
        isLeftMousePressed = false;
        isLeftDraggingForPan = false;
        uiHandledLeftMousePress = false;
        if (camera != null && camera.isManuallyPanning()) {
            camera.stopManualPan();
        }
    }

    private void setupCallbacks() {
        glfwSetCursorPosCallback(window, (win, xpos, ypos) -> {
            double currentX = xpos;
            double currentY = ypos;
            double dx = currentX - lastMouseX;
            double dy = currentY - lastMouseY;

            if (gameInstance == null || camera == null) return;

            if (gameInstance.getCurrentGameState() == Game.GameState.IN_GAME) {
                // Only do in-game hover/drag if map and inputHandlerRef are valid
                if (map != null && inputHandlerRef != null && !isLeftDraggingForPan) {
                    int[] fbWidth = new int[1]; int[] fbHeight = new int[1]; glfwGetFramebufferSize(window, fbWidth, fbHeight);
                    int[] winWidth = new int[1]; int[] winHeight = new int[1]; glfwGetWindowSize(window, winWidth, winHeight);
                    double cScaleX = (fbWidth[0] > 0 && winWidth[0] > 0) ? (double)fbWidth[0]/winWidth[0] : 1.0;
                    double cScaleY = (fbHeight[0] > 0 && winHeight[0] > 0) ? (double)fbHeight[0]/winHeight[0] : 1.0;
                    int physicalMouseX = (int)(xpos * cScaleX);
                    int physicalMouseY = (int)(ypos * cScaleY);
                    int[] hoveredCoords = camera.screenToAccurateMapTile(physicalMouseX, physicalMouseY, map);
                    if (hoveredCoords != null) {
                        inputHandlerRef.setSelectedTile(hoveredCoords[0], hoveredCoords[1]);
                    }
                }

                if (isLeftMousePressed && !isLeftDraggingForPan && !uiHandledLeftMousePress) {
                    double dragDx = currentX - pressMouseX;
                    double dragDy = currentY - pressMouseY;
                    if ((dragDx * dragDx + dragDy * dragDy) > DRAG_THRESHOLD_SQUARED) {
                        isLeftDraggingForPan = true;
                        camera.startManualPan();
                    }
                }

                if (isLeftDraggingForPan) {
                    float[] mapDelta = camera.screenVectorToMapVector((float) -dx, (float) -dy);
                    camera.moveTargetPosition(mapDelta[0], mapDelta[1]);
                }
            } else if (gameInstance.getCurrentGameState() == Game.GameState.MAIN_MENU) {
                int[] fbWidth = new int[1]; int[] fbHeight = new int[1]; glfwGetFramebufferSize(window, fbWidth, fbHeight);
                int[] winWidth = new int[1]; int[] winHeight = new int[1]; glfwGetWindowSize(window, winWidth, winHeight);
                double cScaleX = (fbWidth[0] > 0 && winWidth[0] > 0) ? (double)fbWidth[0]/winWidth[0] : 1.0;
                double cScaleY = (fbHeight[0] > 0 && winHeight[0] > 0) ? (double)fbHeight[0]/winHeight[0] : 1.0;
                float physicalMouseX = (float)(xpos * cScaleX);
                float physicalMouseY = (float)(ypos * cScaleY);
                List<MenuItemButton> menuButtons = gameInstance.getMainMenuButtons();
                if (menuButtons != null) {
                    for (MenuItemButton button : menuButtons) {
                        if(button.isVisible) button.isHovered = button.isMouseOver(physicalMouseX, physicalMouseY);
                        else button.isHovered = false;
                    }
                }
            }
            lastMouseX = currentX;
            lastMouseY = currentY;
        });


            glfwSetMouseButtonCallback(window, (win, buttonId, action, mods) -> {
                if (gameInstance == null || camera == null ) {
                    System.err.println("MouseHandler (ButtonCallback): gameInstance or camera is null!");
                    return;
                }

                int[] fbWidthPixels = new int[1]; int[] fbHeightPixels = new int[1];
                glfwGetFramebufferSize(window, fbWidthPixels, fbHeightPixels);
                int[] windowWidthPoints = new int[1]; int[] windowHeightPoints = new int[1];
                glfwGetWindowSize(window, windowWidthPoints, windowHeightPoints);
                double scaleX = (fbWidthPixels[0] > 0 && windowWidthPoints[0] > 0) ? (double) fbWidthPixels[0] / windowWidthPoints[0] : 1.0;
                double scaleY = (fbHeightPixels[0] > 0 && windowHeightPoints[0] > 0) ? (double) fbHeightPixels[0] / windowHeightPoints[0] : 1.0;
                int mouseX_physical = (int) (lastMouseX * scaleX);
                int mouseY_physical = (int) (lastMouseY * scaleY);

                // Handle Main Menu clicks first
                if (gameInstance.getCurrentGameState() == Game.GameState.MAIN_MENU) {
                    if (buttonId == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_PRESS) {
                        List<MenuItemButton> menuButtons = gameInstance.getMainMenuButtons();
                        if (menuButtons != null) {
                            for (MenuItemButton btn : menuButtons) {
                                if (btn.isVisible && btn.isMouseOver(mouseX_physical, mouseY_physical)) {
                                    // ... (This part for main menu buttons is fine and remains the same)
                                    switch (btn.actionCommand) {
                                        case "NEW_WORLD": gameInstance.createNewWorld(); break;
                                        case "LOAD_WORLD": gameInstance.loadGame(btn.associatedData); break;
                                        case "DELETE_WORLD": gameInstance.deleteWorld(btn.associatedData); break;
                                        case "EXIT_GAME": glfwSetWindowShouldClose(window, true); break;
                                    }
                                    return; // Exit after handling a menu click
                                }
                            }
                        }
                    }
                    return; // Nothing else to do if in main menu
                }

                // --- IN-GAME LOGIC ---
                if (gameInstance.getCurrentGameState() != Game.GameState.IN_GAME || player == null || map == null) {
                    return; // Do nothing if not in-game or context is missing
                }

                if (buttonId == GLFW_MOUSE_BUTTON_LEFT) {
                    if (action == GLFW_PRESS) {
                        isLeftMousePressed = true;
                        pressMouseX = lastMouseX;
                        pressMouseY = lastMouseY;
                        uiHandledLeftMousePress = false; // Reset flag

                        if (gameInstance.isInventoryVisible()) {
                            // Check for clicks inside the inventory panel
                            float slotSizeInv = 50f;
                            float slotMarginInv = 10f;
                            int slotsPerRowInv = 5;
                            List<InventorySlot> slots = player.getInventorySlots();
                            if (!slots.isEmpty()) {
                                int numRows = (int) Math.ceil((double) slots.size() / slotsPerRowInv);
                                float panelWidth = (slotsPerRowInv * slotSizeInv) + ((slotsPerRowInv + 1) * slotMarginInv);

                                // *** THIS IS THE CRITICAL FIX ***
                                // Use the correct right-side calculation for the panel's X position
                                float inventoryMarginX = 30f;
                                float panelX = camera.getScreenWidth() - panelWidth - inventoryMarginX;
                                // *** END OF CRITICAL FIX ***

                                float panelHeight = (numRows * slotSizeInv) + ((numRows + 1) * slotMarginInv);
                                float panelY = (camera.getScreenHeight() - panelHeight) / 2.0f;

                                // Check if the click is within the panel bounds first
                                if (mouseX_physical >= panelX && mouseX_physical <= panelX + panelWidth &&
                                        mouseY_physical >= panelY && mouseY_physical <= panelY + panelHeight) {

                                    uiHandledLeftMousePress = true; // Mark click as handled by UI

                                    // Now, check if the click was on a specific slot to start a drag
                                    float currentSlotX = panelX + slotMarginInv;
                                    float currentSlotY = panelY + slotMarginInv;
                                    int colCount = 0;
                                    for (int i = 0; i < slots.size(); i++) {
                                        if (mouseX_physical >= currentSlotX && mouseX_physical <= currentSlotX + slotSizeInv &&
                                                mouseY_physical >= currentSlotY && mouseY_physical <= currentSlotY + slotSizeInv) {

                                            // Found the clicked slot, start dragging!
                                            gameInstance.startDraggingItem(i);
                                            break; // Exit loop once slot is found
                                        }
                                        currentSlotX += slotSizeInv + slotMarginInv;
                                        colCount++;
                                        if (colCount >= slotsPerRowInv) {
                                            colCount = 0;
                                            currentSlotX = panelX + slotMarginInv;
                                            currentSlotY += slotSizeInv + slotMarginInv;
                                        }
                                    }
                                }
                            }
                        }

                        // If UI did not handle the click, it might be a world action
                        if (!uiHandledLeftMousePress) {
                            inputHandlerRef.performPlayerActionOnCurrentlySelectedTile();
                        }

                    } else if (action == GLFW_RELEASE) {
                        isLeftMousePressed = false;

                        if (gameInstance.isDraggingItem()) {
                            int dropSlotIndex = -1; // Default to invalid drop location
                            // Calculate inventory position again to find the drop slot
                            float slotSizeInv = 50f;
                            float slotMarginInv = 10f;
                            int slotsPerRowInv = 5;
                            List<InventorySlot> slots = player.getInventorySlots();
                            int numRows = (int) Math.ceil((double) slots.size() / slotsPerRowInv);
                            float panelWidth = (slotsPerRowInv * slotSizeInv) + ((slotsPerRowInv + 1) * slotMarginInv);
                            float inventoryMarginX = 30f;
                            float panelX = camera.getScreenWidth() - panelWidth - inventoryMarginX;
                            float panelHeight = (numRows * slotSizeInv) + ((numRows + 1) * slotMarginInv);
                            float panelY = (camera.getScreenHeight() - panelHeight) / 2.0f;

                            if (mouseX_physical >= panelX && mouseX_physical <= panelX + panelWidth &&
                                    mouseY_physical >= panelY && mouseY_physical <= panelY + panelHeight) {

                                // Find the specific slot
                                float currentSlotX = panelX + slotMarginInv;
                                float currentSlotY = panelY + slotMarginInv;
                                int colCount = 0;
                                for (int i = 0; i < slots.size(); i++) {
                                    if (mouseX_physical >= currentSlotX && mouseX_physical <= currentSlotX + slotSizeInv &&
                                            mouseY_physical >= currentSlotY && mouseY_physical <= currentSlotY + slotSizeInv) {
                                        dropSlotIndex = i;
                                        break;
                                    }
                                    currentSlotX += slotSizeInv + slotMarginInv;
                                    colCount++;
                                    if (colCount >= slotsPerRowInv) {
                                        colCount = 0;
                                        currentSlotX = panelX + slotMarginInv;
                                        currentSlotY += slotSizeInv + slotMarginInv;
                                    }
                                }
                            }
                            gameInstance.stopDraggingItem(dropSlotIndex);
                        } else if (isLeftDraggingForPan) {
                            camera.stopManualPan();
                            isLeftDraggingForPan = false;
                        }
                    }

                } else if (buttonId == GLFW_MOUSE_BUTTON_RIGHT && action == GLFW_RELEASE) {
                    if (!uiHandledLeftMousePress) {
                        int selectedSlot = gameInstance.getSelectedHotbarSlotIndex();
                        Item itemToPlace = player.getPlaceableItemInSlot(selectedSlot);
                        if (itemToPlace != null) {
                            int[] targetCoords = camera.screenToAccurateMapTile(mouseX_physical, mouseY_physical, map);
                            if (targetCoords != null) {
                                int targetC = targetCoords[0]; int targetR = targetCoords[1];
                                int playerR = player.getTileRow(); int playerC = player.getTileCol();
                                int distance = Math.abs(targetR - playerR) + Math.abs(targetC - playerC);
                                if (distance <= MAX_INTERACTION_DISTANCE +1) {
                                    if (map.placeBlock(targetR, targetC, itemToPlace)) {
                                        if (!player.consumeItemFromSlot(selectedSlot, 1)) {
                                            System.err.println("Placed block but failed to consume item!");
                                        }
                                    }
                                } else {
                                    // System.out.println("Target tile for placement is too far away.");
                                }
                            }
                        } else {
                            // System.out.println("No placeable item selected or slot is empty for right-click.");
                        }
                    }
                }
            
        });

        glfwSetScrollCallback(window, (win, xoffset, yoffset) -> {
            if (camera != null) {
                if (yoffset > 0) {
                    camera.adjustZoom(Constants.CAMERA_ZOOM_SPEED);
                } else if (yoffset < 0) {
                    camera.adjustZoom(-Constants.CAMERA_ZOOM_SPEED);
                }
            }
        });
    }
}
