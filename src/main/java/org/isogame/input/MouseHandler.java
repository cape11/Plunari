package org.isogame.input;

import org.isogame.camera.CameraManager;
import org.isogame.constants.Constants;
import org.isogame.entitiy.PlayerModel; // Ensure 'entity' spelling
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
    private final Map map;
    private final InputHandler inputHandlerRef;
    private final PlayerModel player;
    private final Game gameInstance;

    private double lastMouseX, lastMouseY;
    private boolean isLeftDraggingForPan = false;
    private boolean isLeftMousePressed = false;
    private double pressMouseX, pressMouseY;
    private static final double DRAG_THRESHOLD_SQUARED = 5 * 5;
    private boolean uiHandledLeftMousePress = false;

    public MouseHandler(long window, CameraManager camera, Map map, InputHandler inputHandler, PlayerModel player, Game gameInstance) {
        this.window = window;
        this.camera = camera;
        this.map = map;
        this.inputHandlerRef = inputHandler;
        this.player = player;
        this.gameInstance = gameInstance;
        setupCallbacks();
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

            if (gameInstance.getCurrentGameState() == Game.GameState.IN_GAME && !isLeftDraggingForPan && map != null && inputHandlerRef != null) {
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

            if (isLeftMousePressed && !isLeftDraggingForPan && !uiHandledLeftMousePress &&
                    gameInstance.getCurrentGameState() == Game.GameState.IN_GAME) {
                double dragDx = currentX - pressMouseX;
                double dragDy = currentY - pressMouseY;
                if ((dragDx * dragDx + dragDy * dragDy) > DRAG_THRESHOLD_SQUARED) {
                    isLeftDraggingForPan = true;
                    camera.startManualPan();
                }
            }

            if (isLeftDraggingForPan && gameInstance.getCurrentGameState() == Game.GameState.IN_GAME) {
                float[] mapDelta = camera.screenVectorToMapVector((float) -dx, (float) -dy);
                camera.moveTargetPosition(mapDelta[0], mapDelta[1]);
            }
            lastMouseX = currentX;
            lastMouseY = currentY;

            if (gameInstance.getCurrentGameState() == Game.GameState.MAIN_MENU) {
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
        });

        glfwSetMouseButtonCallback(window, (win, buttonId, action, mods) -> {
            if (gameInstance == null || camera == null || player == null || inputHandlerRef == null || map == null) return;

            int[] fbWidthPixels = new int[1]; int[] fbHeightPixels = new int[1]; glfwGetFramebufferSize(window, fbWidthPixels, fbHeightPixels);
            int[] windowWidthPoints = new int[1]; int[] windowHeightPoints = new int[1]; glfwGetWindowSize(window, windowWidthPoints, windowHeightPoints);
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
                                uiHandledLeftMousePress = true;
                                switch (btn.actionCommand) {
                                    case "NEW_WORLD": gameInstance.createNewWorld(); break;
                                    case "LOAD_WORLD": if (btn.associatedData != null) gameInstance.loadGame(btn.associatedData); break;
                                    case "DELETE_WORLD": if (btn.associatedData != null) gameInstance.deleteWorld(btn.associatedData); break;
                                    case "EXIT_GAME": glfwSetWindowShouldClose(window, true); break;
                                }
                                break;
                            }
                        }
                    }
                }
            } else if (gameInstance.getCurrentGameState() == Game.GameState.IN_GAME) {
                if (buttonId == GLFW_MOUSE_BUTTON_LEFT) {
                    if (action == GLFW_PRESS) {
                        isLeftMousePressed = true;
                        isLeftDraggingForPan = false;
                        pressMouseX = lastMouseX;
                        pressMouseY = lastMouseY;

                        if (gameInstance.isInventoryVisible()) {
                            // Inventory slot click logic
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
                                        gameInstance.setSelectedInventorySlotIndex(i);
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
                            // Hotbar click logic
                            float slotSizeHb = 55f; float slotMarginHb = 6f;
                            int hotbarSlotsToDisplay = Constants.HOTBAR_SIZE;
                            float totalHotbarWidth = (hotbarSlotsToDisplay * slotSizeHb) + ((Math.max(0, hotbarSlotsToDisplay - 1)) * slotMarginHb);
                            float hotbarXStart = (camera.getScreenWidth() - totalHotbarWidth) / 2.0f;
                            float hotbarYStart = camera.getScreenHeight() - slotSizeHb - (slotMarginHb * 3);
                            for (int i = 0; i < hotbarSlotsToDisplay; i++) {
                                float currentSlotX = hotbarXStart + i * (slotSizeHb + slotMarginHb);
                                if (mouseX_physical >= currentSlotX && mouseX_physical <= currentSlotX + slotSizeHb &&
                                        mouseY_physical >= hotbarYStart && mouseY_physical <= hotbarYStart + slotSizeHb) {
                                    gameInstance.setSelectedInventorySlotIndex(i);
                                    uiHandledLeftMousePress = true;
                                    break;
                                }
                            }
                        }

                        if (!isLeftDraggingForPan && !uiHandledLeftMousePress) {
                            inputHandlerRef.performPlayerActionOnCurrentlySelectedTile();
                        }
                    } else if (action == GLFW_RELEASE) {
                        // camera.stopManualPan(); // This is intentionally NOT called here anymore
                        isLeftMousePressed = false;
                        isLeftDraggingForPan = false;
                    }
                } else if (buttonId == GLFW_MOUSE_BUTTON_RIGHT && action == GLFW_RELEASE) {
                    if (!uiHandledLeftMousePress) {
                        int selectedSlot = gameInstance.getSelectedInventorySlotIndex();
                        Item itemToPlace = player.getPlaceableItemInSlot(selectedSlot);
                        if (itemToPlace != null) {
                            int[] targetCoords = camera.screenToAccurateMapTile(mouseX_physical, mouseY_physical, map);
                            if (targetCoords != null) {
                                int targetC = targetCoords[0]; int targetR = targetCoords[1];
                                int playerR = player.getTileRow(); int playerC = player.getTileCol();
                                int distance = Math.abs(targetR - playerR) + Math.abs(targetC - playerC);
                                if (distance <= MAX_INTERACTION_DISTANCE + 1) {
                                    if (map.placeBlock(targetR, targetC, itemToPlace)) {
                                        if (!player.consumeItemFromSlot(selectedSlot, 1)) {
                                            System.err.println("Placed block but failed to consume item!");
                                        }
                                    }
                                } else { System.out.println("Target tile for placement is too far away."); }
                            }
                        } else { System.out.println("No placeable item selected or slot is empty for right-click.");}
                    }
                }
            }
        });

        // --- CORRECTED SCROLL CALLBACK FOR ZOOM ---
        glfwSetScrollCallback(window, (win, xoffset, yoffset) -> {
            if (camera != null) {
                if (yoffset > 0) { // Positive yoffset usually means scroll wheel moved "up" or "forward"
                    camera.adjustZoom(Constants.CAMERA_ZOOM_SPEED); // Zoom In
                } else if (yoffset < 0) { // Negative yoffset usually means scroll wheel moved "down" or "backward"
                    camera.adjustZoom(-Constants.CAMERA_ZOOM_SPEED); // Zoom Out
                }
                // System.out.println("Scroll: yoffset=" + yoffset + ", New Target Zoom=" + camera.targetZoom); // For debugging
            }
        });
    }
}