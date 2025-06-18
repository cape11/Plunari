package org.isogame.input;

import org.isogame.camera.CameraManager;
import org.isogame.constants.Constants;
import org.isogame.entity.PlayerModel;
import org.isogame.game.Game;
import org.isogame.item.Item;
import org.isogame.map.Map;
import org.isogame.tile.Tile;
import org.isogame.ui.MenuItemButton;

import java.util.List;

import static org.isogame.constants.Constants.TORCH_LIGHT_LEVEL;
import static org.lwjgl.glfw.GLFW.*;

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

    public void updateGameReferences(Map map, PlayerModel player, InputHandler inputHandler) {
        this.map = map;
        this.player = player;
        this.inputHandlerRef = inputHandler;
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
        // --- MOUSE MOVEMENT CALLBACK ---
        glfwSetCursorPosCallback(window, (win, xpos, ypos) -> {
            double currentX = xpos;
            double currentY = ypos;
            double dx = currentX - lastMouseX;
            double dy = currentY - lastMouseY;

            if (gameInstance == null || camera == null) return;

            // Calculate physical mouse coordinates once for all states
            int[] fbWidth = new int[1]; int[] fbHeight = new int[1]; glfwGetFramebufferSize(window, fbWidth, fbHeight);
            int[] winWidth = new int[1]; int[] winHeight = new int[1]; glfwGetWindowSize(window, winWidth, winHeight);
            double cScaleX = (fbWidth[0] > 0 && winWidth[0] > 0) ? (double)fbWidth[0]/winWidth[0] : 1.0;
            double cScaleY = (fbHeight[0] > 0 && winHeight[0] > 0) ? (double)fbHeight[0]/winHeight[0] : 1.0;
            int physicalMouseX = (int)(xpos * cScaleX);
            int physicalMouseY = (int)(ypos * cScaleY);

            if (gameInstance.getCurrentGameState() == Game.GameState.IN_GAME) {
                // World Tile Hover
                if (map != null && inputHandlerRef != null && !gameInstance.isInventoryVisible()) {
                    int[] hoveredCoords = camera.screenToAccurateMapTile(physicalMouseX, physicalMouseY, map);
                    if (hoveredCoords != null) {
                        inputHandlerRef.setSelectedTile(hoveredCoords[0], hoveredCoords[1]);
                        if (gameInstance.getPlacementManager() != null && gameInstance.getPlacementManager().isPlacing()) {
                            gameInstance.getPlacementManager().updatePlacement(hoveredCoords[0], hoveredCoords[1]);
                        }

                    }
                }
                // Camera Panning
                if (isLeftMousePressed && !isLeftDraggingForPan && !uiHandledLeftMousePress) {
                    if ((Math.abs(dx) + Math.abs(dy)) > 5) {
                        isLeftDraggingForPan = true;
                        camera.startManualPan();
                    }
                }
                if (isLeftDraggingForPan) {
                    float[] mapDelta = camera.screenVectorToMapVector((float) -dx, (float) -dy);
                    camera.moveTargetPosition(mapDelta[0], mapDelta[1]);
                }
            } else if (gameInstance.getCurrentGameState() == Game.GameState.MAIN_MENU) {
                // Main menu button hover
                List<MenuItemButton> menuButtons = gameInstance.getMainMenuButtons();
                if (menuButtons != null) {
                    for (MenuItemButton button : menuButtons) {
                        button.isHovered = button.isVisible && button.isMouseOver(physicalMouseX, physicalMouseY);
                    }
                }
            }
            lastMouseX = currentX;
            lastMouseY = currentY;
        });

        // --- MOUSE BUTTON CALLBACK (Corrected Structure) ---
        glfwSetMouseButtonCallback(window, (win, buttonId, action, mods) -> {
            if (gameInstance == null || camera == null) return;

            int[] fbWidth = new int[1]; int[] fbHeight = new int[1]; glfwGetFramebufferSize(window, fbWidth, fbHeight);
            int[] winWidth = new int[1]; int[] winHeight = new int[1]; glfwGetWindowSize(window, winWidth, winHeight);
            double cScaleX = (fbWidth[0] > 0 && winWidth[0] > 0) ? (double)fbWidth[0]/winWidth[0] : 1.0;
            double cScaleY = (fbHeight[0] > 0 && winHeight[0] > 0) ? (double)fbHeight[0]/winHeight[0] : 1.0;
            int mouseX_physical = (int)(lastMouseX * cScaleX);
            int mouseY_physical = (int)(lastMouseY * cScaleY);

            switch (gameInstance.getCurrentGameState()) {
                case MAIN_MENU:
                    handleMenuMouseClick(buttonId, action, mouseX_physical, mouseY_physical);
                    break;
                case IN_GAME:
                    handleGameMouseClick(buttonId, action, mouseX_physical, mouseY_physical);
                    break;
            }
        });

        // --- MOUSE SCROLL CALLBACK ---
        glfwSetScrollCallback(window, (win, xoffset, yoffset) -> {
            if (camera != null) {
                camera.adjustZoom(yoffset > 0 ? Constants.CAMERA_ZOOM_SPEED : -Constants.CAMERA_ZOOM_SPEED);
            }
        });
    }

    // --- HELPER METHOD for Main Menu Clicks ---
    private void handleMenuMouseClick(int buttonId, int action, int mouseX, int mouseY) {
        if (buttonId == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_PRESS) {
            List<MenuItemButton> menuButtons = gameInstance.getMainMenuButtons();
            if (menuButtons != null) {
                for (MenuItemButton btn : menuButtons) {
                    if (btn.isVisible && btn.isMouseOver(mouseX, mouseY)) {
                        switch (btn.actionCommand) {
                            case "NEW_WORLD": gameInstance.createNewWorld(); break;
                            case "LOAD_WORLD": gameInstance.loadGame(btn.associatedData); break;
                            case "DELETE_WORLD": gameInstance.deleteWorld(btn.associatedData); break;
                            case "EXIT_GAME": glfwSetWindowShouldClose(window, true); break;
                        }
                        return; // Important: Stop processing after a button click
                    }
                }
            }
        }
    }

    // In MouseHandler.java

    private void handleGameMouseClick(int buttonId, int action, int mouseX, int mouseY) {
        if (player == null || gameInstance.getPlacementManager() == null) return;

        int[] hoveredCoords = camera.screenToAccurateMapTile(mouseX, mouseY, map);

        // --- LEFT MOUSE BUTTON ---
        if (buttonId == GLFW_MOUSE_BUTTON_LEFT) {
            if (action == GLFW_PRESS) {
                isLeftMousePressed = true;
                uiHandledLeftMousePress = false;

                if (gameInstance.isInventoryVisible()) {
                    uiHandledLeftMousePress = checkInventoryClick(mouseX, mouseY) || checkCraftingClick(mouseX, mouseY);
                }
                if (!uiHandledLeftMousePress) {
                    inputHandlerRef.performPlayerActionOnCurrentlySelectedTile();
                }
            } else if (action == GLFW_RELEASE) {
                isLeftMousePressed = false;
                if (gameInstance.isDraggingItem()) {
                    int dropSlot = getInventorySlotAt(mouseX, mouseY);
                    gameInstance.stopDraggingItem(dropSlot);
                } else if (isLeftDraggingForPan) {
                    camera.stopManualPan();
                    isLeftDraggingForPan = false;
                }
            }
        }
        // --- RIGHT MOUSE BUTTON ---
        else if (buttonId == GLFW_MOUSE_BUTTON_RIGHT) {
            if (action == GLFW_PRESS) {
                if (!gameInstance.isInventoryVisible() && hoveredCoords != null) {
                    gameInstance.getPlacementManager().startPlacement(hoveredCoords[0], hoveredCoords[1]);
                }
            } else if (action == GLFW_RELEASE) {
                if (gameInstance.getPlacementManager().isPlacing()) {
                    gameInstance.getPlacementManager().finalizePlacement();
                }
            }
        }
    }

    // --- HELPER METHOD to check for clicks on Inventory Slots ---
    private boolean checkInventoryClick(int mouseX, int mouseY) {
        int slotIndex = getInventorySlotAt(mouseX, mouseY);
        if (slotIndex != -1) {
            // If the click is on a valid slot, start dragging the item.
            gameInstance.startDraggingItem(slotIndex);
            return true; // Click was handled by the UI
        }
        return false; // Click was not on an inventory slot
    }


    // --- HELPER METHOD to check for clicks on Craft Buttons ---
    private boolean checkCraftingClick(int mouseX, int mouseY) {
        // Calculate crafting panel position (should match Renderer)
        float slotSize = 50f, slotMargin = 10f;
        float panelMarginX = 30f, topMarginY = 40f, marginBetweenPanels = 20f;
        int invSlotsPerRow = 5;
        float invPanelWidth = (invSlotsPerRow * slotSize) + ((invSlotsPerRow + 1) * slotMargin);
        float invPanelX = camera.getScreenWidth() - invPanelWidth - panelMarginX;
        float invPanelHeight = (float)(Math.ceil((double) player.getInventorySlots().size() / invSlotsPerRow) * (slotSize + slotMargin)) + slotMargin;
        float invPanelY = topMarginY;
        float craftPanelX = invPanelX;
        float craftPanelY = invPanelY + invPanelHeight + marginBetweenPanels;

        List<org.isogame.crafting.CraftingRecipe> recipes = org.isogame.crafting.RecipeRegistry.getAllRecipes();
        float recipeRowHeight = 50f;
        float craftButtonWidth = 70f, craftButtonHeight = 25f;
        float craftButtonX = craftPanelX + invPanelWidth - craftButtonWidth - slotMargin;
        float currentRecipeY = craftPanelY + slotMargin + 30f;

        for (org.isogame.crafting.CraftingRecipe recipe : recipes) {
            float craftButtonY = currentRecipeY + (recipeRowHeight - craftButtonHeight) / 2f - 2;
            if (gameInstance.canCraft(recipe) && mouseX >= craftButtonX && mouseX <= craftButtonX + craftButtonWidth &&
                    mouseY >= craftButtonY && mouseY <= craftButtonY + craftButtonHeight) {
                gameInstance.doCraft(recipe);
                return true; // Click was handled
            }
            currentRecipeY += recipeRowHeight;
        }
        return false;
    }

    // --- ROBUST HELPER to get slot index from mouse coordinates ---
    // --- ROBUST HELPER to get slot index from mouse coordinates ---
    private int getInventorySlotAt(int mouseX, int mouseY) {
        float slotSize = 50f, slotMargin = 10f;
        float panelMarginX = 30f, topMarginY = 40f;
        int invSlotsPerRow = 5;
        float invPanelWidth = (invSlotsPerRow * slotSize) + ((invSlotsPerRow + 1) * slotMargin);
        float invPanelX = camera.getScreenWidth() - invPanelWidth - panelMarginX;
        float invPanelY = topMarginY;

        if (mouseX < invPanelX || mouseX > invPanelX + invPanelWidth ||
                mouseY < invPanelY) {
            return -1;
        }

        float startX = invPanelX + slotMargin;
        float startY = invPanelY + slotMargin;

        for (int i = 0; i < player.getInventorySlots().size(); i++) {
            int row = i / invSlotsPerRow;
            int col = i % invSlotsPerRow;
            float currentSlotX = startX + col * (slotSize + slotMargin);
            float currentSlotY = startY + row * (slotSize + slotMargin);
            if (mouseX >= currentSlotX && mouseX <= currentSlotX + slotSize &&
                    mouseY >= currentSlotY && mouseY <= currentSlotY + slotSize) {
                return i;
            }
        }
        return -1;
    }





}
