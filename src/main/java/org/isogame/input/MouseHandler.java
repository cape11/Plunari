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

            uiHandledLeftMousePress = false;

            if (gameInstance.getCurrentGameState() == Game.GameState.MAIN_MENU) {
                if (buttonId == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_PRESS) {
                    List<MenuItemButton> menuButtons = gameInstance.getMainMenuButtons();
                    if (menuButtons != null) {
                        for (MenuItemButton btn : menuButtons) {
                            if (btn.isVisible && btn.isMouseOver(mouseX_physical, mouseY_physical)) {
                                System.out.println("MouseHandler: Button '" + btn.text + "' clicked. Action: " + btn.actionCommand);
                                uiHandledLeftMousePress = true;
                                switch (btn.actionCommand) {
                                    case "NEW_WORLD":
                                        System.out.println("MouseHandler: Dispatching NEW_WORLD to gameInstance.");
                                        gameInstance.createNewWorld();
                                        break;
                                    case "LOAD_WORLD":
                                        if (btn.associatedData != null) {
                                            System.out.println("MouseHandler: Dispatching LOAD_WORLD(" + btn.associatedData + ") to gameInstance.");
                                            gameInstance.loadGame(btn.associatedData);
                                        } else {
                                            System.err.println("MouseHandler: Load_World action but associatedData is null for button: " + btn.text);
                                        }
                                        break;
                                    case "DELETE_WORLD":
                                        if (btn.associatedData != null) {
                                            System.out.println("MouseHandler: Dispatching DELETE_WORLD(" + btn.associatedData + ") to gameInstance.");
                                            gameInstance.deleteWorld(btn.associatedData);
                                        } else {
                                            System.err.println("MouseHandler: Delete_World action but associatedData is null for button: " + btn.text);
                                        }
                                        break;
                                    case "EXIT_GAME":
                                        System.out.println("MouseHandler: Dispatching EXIT_GAME.");
                                        glfwSetWindowShouldClose(window, true);
                                        break;
                                    default:
                                        System.err.println("MouseHandler: Unknown menu button action command: " + btn.actionCommand);
                                }
                                break;
                            }
                        }
                    } else {
                        System.err.println("MouseHandler: menuButtons list is null in MAIN_MENU state.");
                    }
                }
            } else if (gameInstance.getCurrentGameState() == Game.GameState.IN_GAME) {
                if (map == null || player == null || inputHandlerRef == null) { // Ensure context for in-game actions
                    System.err.println("MouseHandler (ButtonCallback): map, player, or inputHandlerRef is null in IN_GAME state.");
                    return;
                }

                if (buttonId == GLFW_MOUSE_BUTTON_LEFT) {
                    if (action == GLFW_PRESS) {
                        isLeftMousePressed = true;
                        isLeftDraggingForPan = false;
                        pressMouseX = lastMouseX;
                        pressMouseY = lastMouseY;

                        if (gameInstance.isInventoryVisible()) {
                            float panelX = 0, panelY = 0, panelWidth = 0, panelHeight = 0;
                            float slotSizeInv = 50f; float slotMarginInv = 10f; int slotsPerRowInv = 5;
                            List<InventorySlot> slots = player.getInventorySlots();
                            if (!slots.isEmpty()) {
                                int numRows = (int) Math.ceil((double) slots.size() / slotsPerRowInv);
                                panelWidth = (slotsPerRowInv * slotSizeInv) + ((slotsPerRowInv + 1) * slotMarginInv);
                                panelHeight = (numRows * slotSizeInv) + ((numRows + 1) * slotMarginInv);
                                panelX = (camera.getScreenWidth() - panelWidth) / 2.0f;
                                panelY = (camera.getScreenHeight() - panelHeight) / 2.0f;
                                float currentSlotX = panelX + slotMarginInv; float currentSlotY = panelY + slotMarginInv;
                                int colCount = 0;
                                for (int i = 0; i < slots.size(); i++) {
                                    if (mouseX_physical >= currentSlotX && mouseX_physical <= currentSlotX + slotSizeInv &&
                                            mouseY_physical >= currentSlotY && mouseY_physical <= currentSlotY + slotSizeInv) {
                                        gameInstance.setSelectedHotbarSlotIndex(i);
                                        uiHandledLeftMousePress = true; break;
                                    }
                                    currentSlotX += slotSizeInv + slotMarginInv; colCount++;
                                    if (colCount >= slotsPerRowInv) { colCount = 0; currentSlotX = panelX + slotMarginInv; currentSlotY += slotSizeInv + slotMarginInv; }
                                }
                                if (!uiHandledLeftMousePress && mouseX_physical >= panelX && mouseX_physical <= panelX + panelWidth &&
                                        mouseY_physical >= panelY && mouseY_physical <= panelY + panelHeight) {
                                    uiHandledLeftMousePress = true;
                                }
                            }
                        } else if (gameInstance.isShowHotbar()) {
                            float slotSizeHb = 55f; float slotMarginHb = 6f;
                            int hotbarSlotsToDisplay = Constants.HOTBAR_SIZE;
                            float totalHotbarWidth = (hotbarSlotsToDisplay * slotSizeHb) + ((Math.max(0, hotbarSlotsToDisplay - 1)) * slotMarginHb);
                            float hotbarXStart = (camera.getScreenWidth() - totalHotbarWidth) / 2.0f;
                            float hotbarYStart = camera.getScreenHeight() - slotSizeHb - (slotMarginHb * 3);
                            for (int i = 0; i < hotbarSlotsToDisplay; i++) {
                                float currentSlotX = hotbarXStart + i * (slotSizeHb + slotMarginHb);
                                if (mouseX_physical >= currentSlotX && mouseX_physical <= currentSlotX + slotSizeHb &&
                                        mouseY_physical >= hotbarYStart && mouseY_physical <= hotbarYStart + slotSizeHb) {
                                    gameInstance.setSelectedHotbarSlotIndex(i);
                                    uiHandledLeftMousePress = true;
                                    break;
                                }
                            }
                        }
                        if (!isLeftDraggingForPan && !uiHandledLeftMousePress) {
                            inputHandlerRef.performPlayerActionOnCurrentlySelectedTile();
                        }

                    } else if (action == GLFW_RELEASE) {
                        isLeftMousePressed = false;
                        if (isLeftDraggingForPan) {
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
