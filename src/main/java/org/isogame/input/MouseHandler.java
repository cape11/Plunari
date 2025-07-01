// src/main/java/org/isogame/input/MouseHandler.java
package org.isogame.input;

import org.isogame.camera.CameraManager;
import org.isogame.constants.Constants;
import org.isogame.entity.PlayerModel;
import org.isogame.game.Game;
import org.isogame.map.Map;
import org.isogame.ui.MenuItemButton;

import java.util.List;

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
    private boolean uiHandledLeftMousePress = false;

    // These fields store the properly scaled, physical pixel coordinates of the mouse
    private float mouseX_physical;
    private float mouseY_physical;

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

    // Getters for other classes to use the corrected physical coordinates
    public float getMouseX() { return mouseX_physical; }
    public float getMouseY() { return mouseY_physical; }

    private void setupCallbacks() {
        glfwSetCursorPosCallback(window, (win, xpos, ypos) -> {
            if (gameInstance == null || camera == null) return;

            // This scaling logic correctly converts window coordinates to physical framebuffer coordinates
            int[] fbWidth = new int[1]; int[] fbHeight = new int[1]; glfwGetFramebufferSize(window, fbWidth, fbHeight);
            int[] winWidth = new int[1]; int[] winHeight = new int[1]; glfwGetWindowSize(window, winWidth, winHeight);
            double cScaleX = (fbWidth[0] > 0 && winWidth[0] > 0) ? (double)fbWidth[0]/winWidth[0] : 1.0;
            double cScaleY = (fbHeight[0] > 0 && winHeight[0] > 0) ? (double)fbHeight[0]/winHeight[0] : 1.0;

            mouseX_physical = (float)(xpos * cScaleX);
            mouseY_physical = (float)(ypos * cScaleY);

            double dx = xpos - lastMouseX;
            double dy = ypos - lastMouseY;

            if (gameInstance.getGameStateManager().getCurrentState() instanceof org.isogame.game.states.InGameState) {

                // --- Logic for when the inventory is CLOSED ---
                if (map != null && inputHandlerRef != null && !gameInstance.isInventoryVisible()) {
                    int[] hoveredCoords = camera.screenToAccurateMapTile((int)mouseX_physical, (int)mouseY_physical, map);
                    if (hoveredCoords != null) {
                        inputHandlerRef.setSelectedTile(hoveredCoords[0], hoveredCoords[1]);
                        if (gameInstance.getPlacementManager() != null && gameInstance.getPlacementManager().isPlacing()) {
                            gameInstance.getPlacementManager().updatePlacement(hoveredCoords[0], hoveredCoords[1]);
                        }
                    }
                }
                // --- Logic for when the inventory is OPEN ---
                if (gameInstance.isInventoryVisible()) {
                    org.isogame.crafting.CraftingRecipe recipe = getCraftingRecipeAt((int)mouseX_physical, (int)mouseY_physical);
                    gameInstance.setHoveredRecipe(recipe);
                } else {
                    // Ensure the tooltip disappears when inventory is closed
                    gameInstance.setHoveredRecipe(null);
                }
                // --- END OF NEW CODE ---

                if (isLeftMousePressed && !isLeftDraggingForPan && !uiHandledLeftMousePress) {
                    if ((Math.abs(dx) + Math.abs(dy)) > 5) { isLeftDraggingForPan = true; camera.startManualPan(); }
                }
                if (isLeftDraggingForPan) {
                    float[] mapDelta = camera.screenVectorToMapVector((float) -dx, (float) -dy);
                    camera.moveTargetPosition(mapDelta[0], mapDelta[1]);
                }
            } else if (gameInstance.getGameStateManager().getCurrentState() instanceof org.isogame.game.states.MainMenuState) {
                List<MenuItemButton> menuButtons = gameInstance.getMainMenuButtons();
                if (menuButtons != null) {
                    for (MenuItemButton button : menuButtons) {
                        button.isHovered = button.isVisible && button.isMouseOver(mouseX_physical, mouseY_physical);
                    }
                }
            }
            lastMouseX = xpos;
            lastMouseY = ypos;

        });

        glfwSetMouseButtonCallback(window, (win, buttonId, action, mods) -> {
            if (gameInstance == null || camera == null) return;
            if (gameInstance.getGameStateManager().getCurrentState() instanceof org.isogame.game.states.MainMenuState) {
                handleMenuMouseClick(buttonId, action, (int)mouseX_physical, (int)mouseY_physical);
            } else if (gameInstance.getGameStateManager().getCurrentState() instanceof org.isogame.game.states.InGameState) {
                handleGameMouseClick(buttonId, action, (int)mouseX_physical, (int)mouseY_physical);
            }
        });

        glfwSetScrollCallback(window, (win, xoffset, yoffset) -> {
            if (camera != null) {
                camera.adjustZoom(yoffset > 0 ? Constants.CAMERA_ZOOM_SPEED : -Constants.CAMERA_ZOOM_SPEED);
            }
        });
    }

    private org.isogame.crafting.CraftingRecipe getCraftingRecipeAt(int mouseX, int mouseY) {
        if (player == null || !gameInstance.isInventoryVisible()) return null;

        // Layout constants from UIManager
        float slotSize = 50f, slotMargin = 10f;
        float panelMarginX = 30f, topMarginY = 40f, marginBetweenPanels = 20f;
        int invSlotsPerRow = 5;
        float invPanelWidth = (invSlotsPerRow * slotSize) + ((invSlotsPerRow + 1) * slotMargin);
        float invPanelX = camera.getScreenWidth() - invPanelWidth - panelMarginX;
        float invPanelHeight = (float)(Math.ceil((double) player.getInventorySlots().size() / invSlotsPerRow) * (slotSize + slotMargin)) + slotMargin;
        float invPanelY = topMarginY;
        float craftPanelX = invPanelX;
        float craftPanelY = invPanelY + invPanelHeight + marginBetweenPanels;
        float recipeRowHeight = 50f;

        // Check if mouse is within the crafting panel's horizontal bounds
        if (mouseX < craftPanelX || mouseX > craftPanelX + invPanelWidth) {
            return null;
        }

        float currentRecipeY = craftPanelY + slotMargin + 30f;
        List<org.isogame.crafting.CraftingRecipe> recipes = org.isogame.crafting.RecipeRegistry.getAllRecipes();

        for (org.isogame.crafting.CraftingRecipe recipe : recipes) {
            if (mouseY >= currentRecipeY && mouseY <= currentRecipeY + recipeRowHeight) {
                return recipe; // Found the hovered recipe
            }

            currentRecipeY += recipeRowHeight;
        }

        return null; // No recipe is being hovered
    }

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
                        return;
                    }
                }
            }
        }
    }

    private void handleGameMouseClick(int buttonId, int action, int mouseX, int mouseY) {
        if (player == null || gameInstance.getPlacementManager() == null || gameInstance.getUiManager() == null) return;

        if (buttonId == GLFW_MOUSE_BUTTON_LEFT) {
            if (action == GLFW_PRESS) {
                isLeftMousePressed = true;
                uiHandledLeftMousePress = false;

                // --- NEW CLICK LOGIC ---
                // 1. First, check if we clicked on a furnace slot to start a drag.
                if (checkFurnaceClick(mouseX, mouseY)) {
                    uiHandledLeftMousePress = true;
                }
                // --- END NEW LOGIC ---

                // 2. If not, check for other UI interactions (like closing the furnace).
                if (!uiHandledLeftMousePress && gameInstance.getUiManager().isFurnaceUiVisible()) {
                    boolean onFurnacePanel = gameInstance.getUiManager().isMouseOverFurnaceUI(mouseX, mouseY);
                    boolean onInventoryPanel = (getInventorySlotAt(mouseX, mouseY) != -1);
                    if (!onFurnacePanel && !onInventoryPanel) {
                        gameInstance.getUiManager().closeFurnaceUI();
                        uiHandledLeftMousePress = true;
                    }
                }

                // 3. If still no UI handled the click, check the inventory and crafting panel.
                if (!uiHandledLeftMousePress && gameInstance.isInventoryVisible()) {
                    uiHandledLeftMousePress = checkInventoryClick(mouseX, mouseY) || checkCraftingClick(mouseX, mouseY);
                }

                // 4. Finally, if nothing else handled it, perform a world action.
                if (!uiHandledLeftMousePress) {
                    inputHandlerRef.performPlayerActionOnCurrentlySelectedTile();
                }

            } else if (action == GLFW_RELEASE) {
                isLeftMousePressed = false;
                if (gameInstance.isDraggingItem()) {
                    // --- NEW DROP LOGIC ---
                    // 1. Check for a furnace drop first.
                    String furnaceSlot = gameInstance.getUiManager().getFurnaceSlotAt(mouseX, mouseY);
                    if (furnaceSlot != null) {
                        gameInstance.dropItemOnFurnace(furnaceSlot);
                    } else {
                        // 2. If not on furnace, check for an inventory drop.
                        int dropSlot = getInventorySlotAt(mouseX, mouseY);
                        gameInstance.stopDraggingItem(dropSlot);
                    }
                    // --- END NEW LOGIC ---
                } else if (isLeftDraggingForPan) {
                    camera.stopManualPan();
                    isLeftDraggingForPan = false;
                }
            }
        } else if (buttonId == GLFW_MOUSE_BUTTON_RIGHT) {
            if (action == GLFW_PRESS) {
                if (!gameInstance.isInventoryVisible()) {
                    gameInstance.getPlacementManager().startPlacement(inputHandlerRef.getSelectedRow(), inputHandlerRef.getSelectedCol());
                }
            } else if (action == GLFW_RELEASE) {
                if (gameInstance.getPlacementManager().isPlacing()) {
                    gameInstance.getPlacementManager().finalizePlacement();
                }
            }
        }
    }

    // In MouseHandler.java
    private boolean checkFurnaceClick(int mouseX, int mouseY) {
        if (!gameInstance.getUiManager().isFurnaceUiVisible()) {
            return false;
        }

        String furnaceSlot = gameInstance.getUiManager().getFurnaceSlotAt(mouseX, mouseY);
        if (furnaceSlot != null) {
            // We clicked on a furnace slot, tell the game to start the drag.
            gameInstance.startDraggingItemFromFurnace(furnaceSlot);
            return true; // The click was handled.
        }
        return false;
    }

    private boolean checkInventoryClick(int mouseX, int mouseY) {
        int slotIndex = getInventorySlotAt(mouseX, mouseY);
        if (slotIndex != -1) {
            gameInstance.startDraggingItem(slotIndex);
            return true;
        }
        return false;
    }

    private boolean checkCraftingClick(int mouseX, int mouseY) {
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
                return true;
            }
            currentRecipeY += recipeRowHeight;
        }
        return false;
    }

    private int getInventorySlotAt(int mouseX, int mouseY) {
        float slotSize = 50f, slotMargin = 10f;
        float panelMarginX = 30f, topMarginY = 40f;
        int invSlotsPerRow = 5;
        float invPanelWidth = (invSlotsPerRow * slotSize) + ((invSlotsPerRow + 1) * slotMargin);
        float invPanelX = camera.getScreenWidth() - invPanelWidth - panelMarginX;
        float invPanelY = topMarginY;
        if (mouseX < invPanelX || mouseX > invPanelX + invPanelWidth || mouseY < invPanelY) {
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