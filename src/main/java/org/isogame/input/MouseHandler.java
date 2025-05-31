package org.isogame.input;

import org.isogame.camera.CameraManager;
import org.isogame.entitiy.PlayerModel;
import org.isogame.game.Game;
import org.isogame.inventory.InventorySlot;
import org.isogame.item.Item;
import org.isogame.map.Map;
import org.isogame.map.PathNode;
import org.isogame.map.AStarPathfinder;
import org.isogame.ui.MenuItemButton; // Import MenuItemButton

import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.isogame.constants.Constants.*;

public class MouseHandler {
    private final long window;
    private final CameraManager camera;
    private final Map map;
    private final InputHandler inputHandler; // To get Game instance
    private final PlayerModel player;
    private final AStarPathfinder pathfinder;
    private final Game gameInstance; // Store Game instance directly

    private double lastMouseX, lastMouseY;
    private boolean isLeftDraggingForPan = false;
    private boolean isLeftMousePressed = false;
    private double pressMouseX, pressMouseY;
    private static final double DRAG_THRESHOLD_SQUARED = 5 * 5;
    private boolean uiHandledLeftMousePress = false; // Flag to prevent game world interaction if UI was clicked

    public MouseHandler(long window, CameraManager camera, Map map, InputHandler inputHandler, PlayerModel player, Game gameInstance) {
        this.window = window;
        this.camera = camera;
        this.map = map;
        this.inputHandler = inputHandler;
        this.player = player;
        this.pathfinder = new AStarPathfinder();
        this.gameInstance = gameInstance; // Store the Game instance
        setupCallbacks();
    }

    // Call this from Game when transitioning from MAIN_MENU to IN_GAME
    // to ensure dragging flags are reset.
    public void resetLeftMouseDragFlags() {
        isLeftMousePressed = false;
        isLeftDraggingForPan = false;
        uiHandledLeftMousePress = false;
    }


    private void setupCallbacks() {
        glfwSetCursorPosCallback(window, (win, xpos, ypos) -> {
            double currentX = xpos;
            double currentY = ypos;
            double dx = currentX - lastMouseX;
            double dy = currentY - lastMouseY;

            if (isLeftMousePressed && !isLeftDraggingForPan && !uiHandledLeftMousePress && gameInstance.getCurrentGameState() == Game.GameState.IN_GAME) { // Only pan in-game
                double dragDx = currentX - pressMouseX;
                double dragDy = currentY - pressMouseY;
                if ((dragDx * dragDx + dragDy * dragDy) > DRAG_THRESHOLD_SQUARED) {
                    isLeftDraggingForPan = true;
                }
            }

            if (isLeftDraggingForPan && camera != null && gameInstance.getCurrentGameState() == Game.GameState.IN_GAME) { // Only pan in-game
                float[] mapDelta = camera.screenVectorToMapVector((float) -dx, (float) -dy); //
                camera.moveTargetPosition(mapDelta[0], mapDelta[1]); //
            }
            lastMouseX = currentX;
            lastMouseY = currentY;

            // Hover effects for main menu (could also be in Game.updateMainMenu)
            if (gameInstance.getCurrentGameState() == Game.GameState.MAIN_MENU && camera != null) {
                int[] fbWidth = new int[1]; int[] fbHeight = new int[1];
                glfwGetFramebufferSize(window, fbWidth, fbHeight);
                int[] winWidth = new int[1]; int[] winHeight = new int[1];
                glfwGetWindowSize(window, winWidth, winHeight);
                double cScaleX = (fbWidth[0] > 0 && winWidth[0] > 0) ? (double)fbWidth[0]/winWidth[0] : 1.0;
                double cScaleY = (fbHeight[0] > 0 && winHeight[0] > 0) ? (double)fbHeight[0]/winHeight[0] : 1.0;
                float physicalMouseX = (float)(xpos * cScaleX);
                float physicalMouseY = (float)(ypos * cScaleY);

                for (MenuItemButton button : gameInstance.getMainMenuButtons()) {
                    button.isHovered = button.isMouseOver(physicalMouseX, physicalMouseY);
                }
            }
        });

        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            // Ensure gameInstance is available
            if (gameInstance == null) {
                System.err.println("MouseHandler: Game instance is null! Cannot handle mouse button.");
                return;
            }

            // Get physical mouse coordinates (scaled for DPI)
            int[] fbWidthPixels = new int[1]; int[] fbHeightPixels = new int[1];
            glfwGetFramebufferSize(window, fbWidthPixels, fbHeightPixels);
            int[] windowWidthPoints = new int[1]; int[] windowHeightPoints = new int[1];
            glfwGetWindowSize(window, windowWidthPoints, windowHeightPoints);
            double scaleX = (fbWidthPixels[0] > 0 && windowWidthPoints[0] > 0) ? (double) fbWidthPixels[0] / windowWidthPoints[0] : 1.0;
            double scaleY = (fbHeightPixels[0] > 0 && windowHeightPoints[0] > 0) ? (double) fbHeightPixels[0] / windowHeightPoints[0] : 1.0;
            int mouseX_physical = (int) (lastMouseX * scaleX);
            int mouseY_physical = (int) (lastMouseY * scaleY);

            uiHandledLeftMousePress = false; // Reset per click start

            if (gameInstance.getCurrentGameState() == Game.GameState.MAIN_MENU) {
                if (button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_PRESS) {
                    List<MenuItemButton> menuButtons = gameInstance.getMainMenuButtons();
                    for (MenuItemButton btn : menuButtons) {
                        if (btn.isVisible && btn.isMouseOver(mouseX_physical, mouseY_physical)) {
                            uiHandledLeftMousePress = true; // UI handled this press
                            System.out.println("Main Menu: Clicked on button '" + btn.text + "' Action: " + btn.actionCommand);
                            switch (btn.actionCommand) {
                                case "NEW_WORLD":
                                    gameInstance.createNewWorld();
                                    break;
                                case "LOAD_WORLD":
                                    if (btn.associatedData != null) {
                                        gameInstance.loadGame(btn.associatedData); // associatedData is worldFileName
                                    }
                                    break;
                                case "DELETE_WORLD":
                                    if (btn.associatedData != null) {
                                        // Consider adding a confirmation step here in a real game
                                        gameInstance.deleteWorld(btn.associatedData); // associatedData is worldName (not filename here)
                                    }
                                    break;
                                case "EXIT_GAME":
                                    glfwSetWindowShouldClose(window, true);
                                    break;
                            }
                            break; // Click handled, no need to check other buttons
                        }
                    }
                }
            } else if (gameInstance.getCurrentGameState() == Game.GameState.IN_GAME) {
                // --- In-Game Mouse Logic ---
                if (button == GLFW_MOUSE_BUTTON_LEFT) {
                    if (action == GLFW_PRESS) {
                        isLeftMousePressed = true;
                        isLeftDraggingForPan = false;
                        // uiHandledLeftMousePress = false; // Reset earlier, ensure it's correctly scoped
                        pressMouseX = lastMouseX;
                        pressMouseY = lastMouseY;

                        if (gameInstance.isInventoryVisible()) { //
                            // Inventory UI Click Handling
                            int slotsPerRow = 5; //
                            float slotSize = 50f; //
                            float slotMargin = 10f; //
                            List<InventorySlot> slots = player.getInventorySlots(); //
                            int numRows = (slots.isEmpty()) ? 1 : (int) Math.ceil((double) slots.size() / slotsPerRow); //
                            float panelWidth = (slotsPerRow * slotSize) + ((slotsPerRow + 1) * slotMargin); //
                            float panelHeight = (numRows * slotSize) + ((numRows + 1) * slotMargin); //
                            float panelX = (camera.getScreenWidth() - panelWidth) / 2.0f; //
                            float panelY = (camera.getScreenHeight() - panelHeight) / 2.0f; //
                            float currentSlotX = panelX + slotMargin; //
                            float currentSlotY = panelY + slotMargin; //
                            int colCount = 0; //

                            for (int i = 0; i < slots.size(); i++) { //
                                if (mouseX_physical >= currentSlotX && mouseX_physical <= currentSlotX + slotSize &&
                                        mouseY_physical >= currentSlotY && mouseY_physical <= currentSlotY + slotSize) { //
                                    gameInstance.setSelectedInventorySlotIndex(i); //
                                    uiHandledLeftMousePress = true; //
                                    break; //
                                }
                                currentSlotX += slotSize + slotMargin; //
                                colCount++; //
                                if (colCount >= slotsPerRow) { //
                                    colCount = 0; //
                                    currentSlotX = panelX + slotMargin; //
                                    currentSlotY += slotSize + slotMargin; //
                                }
                            }
                            if (!uiHandledLeftMousePress) { // If click was in inventory area but not on a slot
                                if (mouseX_physical >= panelX && mouseX_physical <= panelX + panelWidth &&
                                        mouseY_physical >= panelY && mouseY_physical <= panelY + panelHeight) {
                                    uiHandledLeftMousePress = true; // Click was on panel, consume it
                                }
                                // gameInstance.setSelectedInventorySlotIndex(-1); // Deselect if clicked outside slots but on panel? Or only on explicit close?
                            }
                        }
                    } else if (action == GLFW_RELEASE) {
                        if (!isLeftDraggingForPan && !uiHandledLeftMousePress) {
                            // Game World Interaction (Pathfinding)
                            if (camera != null && map != null && inputHandler != null && player != null && pathfinder != null) { //
                                int[] accurateCoords = camera.screenToAccurateMapTile(mouseX_physical, mouseY_physical, map); //
                                if (accurateCoords != null) { //
                                    inputHandler.setSelectedTile(accurateCoords[0], accurateCoords[1]); //
                                    List<PathNode> path = pathfinder.findPath( //
                                            player.getTileRow(), player.getTileCol(), //
                                            accurateCoords[1], accurateCoords[0], map); //
                                    player.setPath(path); //
                                }
                            }
                        }
                        isLeftMousePressed = false; //
                        isLeftDraggingForPan = false; //
                        // uiHandledLeftMousePress is reset at the start of a new press
                    }
                } else if (button == GLFW_MOUSE_BUTTON_RIGHT && action == GLFW_RELEASE) {
                    if (!uiHandledLeftMousePress) { // Don't place blocks if UI was interacted with
                        // Block Placement
                        int currentSelectedSlot = player.getSelectedHotbarSlotIndex(); // Get current selected hotbar slot
                        Item itemToPlace = player.getPlaceableItemInSlot(currentSelectedSlot);


                        if (itemToPlace != null) { //
                            int[] targetCoords = camera.screenToAccurateMapTile(mouseX_physical, mouseY_physical, map); //
                            if (targetCoords != null) { //
                                int targetC = targetCoords[0]; //
                                int targetR = targetCoords[1]; //
                                int playerR = player.getTileRow(); //
                                int playerC = player.getTileCol(); //
                                int distance = Math.abs(targetR - playerR) + Math.abs(targetC - playerC); //

                                // Use MAX_INTERACTION_DISTANCE from Constants
                                if (distance <= MAX_INTERACTION_DISTANCE) { //
                                    if (map.placeBlock(targetR, targetC, itemToPlace)) { //
                                        // Consume from the *selected hotbar slot* not a generic slot.
                                        if (!player.consumeItemFromSlot(currentSelectedSlot, 1)) { //
                                            System.err.println("Placed block but failed to consume item! Reverting (TODO)."); //
                                        }
                                    }
                                } else { //
                                    System.out.println("Target tile for placement is too far away."); //
                                }
                            }
                        } else { //
                            System.out.println("No placeable item selected or slot is empty for right-click placement."); //
                        }
                    }
                }
            }
            // After all press/release logic, if it was a release, reset uiHandledLeftMousePress for the next click cycle.
            // Actually, it's better to reset it at the START of a new PRESS action.
            if (action == GLFW_RELEASE) {
                // isLeftMousePressed = false; // Already handled for GLFW_MOUSE_BUTTON_LEFT
                // isLeftDraggingForPan = false; // Already handled
            }
        });

        glfwSetScrollCallback(window, (win, xoffset, yoffset) -> {
            if (camera != null) { //
                if (yoffset > 0) camera.adjustZoom(CAMERA_ZOOM_SPEED); //
                else if (yoffset < 0) camera.adjustZoom(-CAMERA_ZOOM_SPEED); //
            }
        });
    }
}