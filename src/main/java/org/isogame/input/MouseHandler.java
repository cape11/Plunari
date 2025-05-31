package org.isogame.input;

import org.isogame.camera.CameraManager;
import org.isogame.entitiy.PlayerModel;
import org.isogame.game.Game;
import org.isogame.inventory.InventorySlot;
import org.isogame.item.Item;
import org.isogame.map.Map;
import org.isogame.map.PathNode;
import org.isogame.map.AStarPathfinder;

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
    private boolean isLeftDraggingForPan = false;
    private boolean isLeftMousePressed = false;
    private double pressMouseX, pressMouseY;
    private static final double DRAG_THRESHOLD_SQUARED = 5 * 5;
    private boolean uiHandledLeftMousePress = false;

    // Constants for menu layout matching Game.renderMainMenu()
    private static final float MENU_ITEM_HEIGHT = 18f; // Approximate height of a text line
    private static final float MENU_PANEL_TOP_PADDING = 10f; // Padding inside the debug panel
    private static final float MENU_TITLE_SPACER_HEIGHT = MENU_ITEM_HEIGHT * 2; // For "--- ISO GAME ---" and blank line
    private static final float MENU_CREATE_WORLD_OFFSET_Y = MENU_PANEL_TOP_PADDING + MENU_TITLE_SPACER_HEIGHT;
    private static final float MENU_WORLDS_HEADER_OFFSET_Y = MENU_CREATE_WORLD_OFFSET_Y + MENU_ITEM_HEIGHT * 2; // After "Create" and a spacer
    private static final float MENU_EXIT_OFFSET_FROM_LAST_WORLD_Y = MENU_ITEM_HEIGHT * 2; // After last world and a spacer

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

            if (isLeftMousePressed && !isLeftDraggingForPan && !uiHandledLeftMousePress) {
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
            int[] fbWidthPixels = new int[1]; int[] fbHeightPixels = new int[1];
            glfwGetFramebufferSize(window, fbWidthPixels, fbHeightPixels);
            int[] windowWidthPoints = new int[1]; int[] windowHeightPoints = new int[1];
            glfwGetWindowSize(window, windowWidthPoints, windowHeightPoints);
            double scaleX = (fbWidthPixels[0] > 0 && windowWidthPoints[0] > 0) ? (double) fbWidthPixels[0] / windowWidthPoints[0] : 1.0;
            double scaleY = (fbHeightPixels[0] > 0 && windowHeightPoints[0] > 0) ? (double) fbHeightPixels[0] / windowHeightPoints[0] : 1.0;
            int mouseX_physical = (int) (lastMouseX * scaleX);
            int mouseY_physical = (int) (lastMouseY * scaleY);

            Game game = null;
            if (inputHandler != null) game = inputHandler.getGameInstance();
            if (game == null) { System.err.println("MouseHandler: Game instance is null!"); return; }

            if (game.getCurrentGameState() == Game.GameState.MAIN_MENU) {
                if (button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_PRESS) {
                    uiHandledLeftMousePress = true;

                    float panelWidth = 400f;
                    List<String> currentSaveFiles = game.getAvailableSaveFiles();
                    float panelHeight = 300f + (currentSaveFiles.size() * 20f);
                    panelHeight = Math.min(panelHeight, camera.getScreenHeight() * 0.8f);
                    float panelX = (camera.getScreenWidth() - panelWidth) / 2f;
                    float panelY = (camera.getScreenHeight() - panelHeight) / 2f;

                    float clickDetectionWidth = panelWidth - 20; // Inner clickable width
                    float clickDetectionXStart = panelX + 10; // Inner clickable start

                    // 1. "Create New World" area
                    float createWorldMinY = panelY + MENU_CREATE_WORLD_OFFSET_Y;
                    float createWorldMaxY = createWorldMinY + MENU_ITEM_HEIGHT;

                    // 2. "Exit Game" area (calculated based on number of worlds)
                    float exitGameMinY = panelY + MENU_WORLDS_HEADER_OFFSET_Y + (currentSaveFiles.size() * MENU_ITEM_HEIGHT) + MENU_EXIT_OFFSET_FROM_LAST_WORLD_Y;
                    float exitGameMaxY = exitGameMinY + MENU_ITEM_HEIGHT;


                    if (mouseX_physical >= clickDetectionXStart && mouseX_physical <= clickDetectionXStart + clickDetectionWidth) {
                        if (mouseY_physical >= createWorldMinY && mouseY_physical <= createWorldMaxY) {
                            System.out.println("Main Menu: Create New World Clicked!");
                            game.createNewWorld(); // This will also set game state to IN_GAME
                        } else if (mouseY_physical >= exitGameMinY && mouseY_physical <= exitGameMaxY) {
                            System.out.println("Main Menu: Exit Clicked!");
                            glfwSetWindowShouldClose(window, true);
                        } else {
                            // Check clicks on world list
                            float currentWorldY = panelY + MENU_WORLDS_HEADER_OFFSET_Y;
                            for (int i = 0; i < currentSaveFiles.size(); i++) {
                                String worldFileName = currentSaveFiles.get(i);
                                float worldMinY = currentWorldY + (i * MENU_ITEM_HEIGHT);
                                float worldMaxY = worldMinY + MENU_ITEM_HEIGHT;

                                // Define a smaller clickable area for "DEL" part
                                float deleteButtonWidth = 50f; // Width of the "(DEL)" clickable area
                                float deleteButtonXStart = clickDetectionXStart + clickDetectionWidth - deleteButtonWidth - 5; // Position it to the right

                                if (mouseY_physical >= worldMinY && mouseY_physical <= worldMaxY) {
                                    if (mouseX_physical >= deleteButtonXStart && mouseX_physical <= deleteButtonXStart + deleteButtonWidth) {
                                        System.out.println("Main Menu: Delete World '" + worldFileName + "' Clicked!");
                                        // TODO: Add a confirmation step before deleting
                                        game.deleteWorld(worldFileName.replace(".json",""));
                                        // uiHandledLeftMousePress remains true
                                        break;
                                    } else if (mouseX_physical >= clickDetectionXStart && mouseX_physical < deleteButtonXStart) { // Clicked on world name, not delete
                                        System.out.println("Main Menu: Load World '" + worldFileName + "' Clicked!");
                                        if (game.loadGame(worldFileName)) { // loadGame uses filename
                                            // game.setCurrentGameState(Game.GameState.IN_GAME); // loadGame now handles this
                                        } else {
                                            System.err.println("Failed to load world: " + worldFileName);
                                            // Stay in main menu, maybe show an error
                                        }
                                        // uiHandledLeftMousePress remains true
                                        break;
                                    }
                                }
                            }
                            if (! (mouseY_physical >= createWorldMinY && mouseY_physical <= exitGameMaxY) ) { // If click was not in any known button area
                                uiHandledLeftMousePress = false;
                            }
                        }
                    } else {
                        uiHandledLeftMousePress = false;
                    }
                } else {
                    uiHandledLeftMousePress = false; // Not a left press for UI
                }
            } else if (game.getCurrentGameState() == Game.GameState.IN_GAME) {
                // --- In-Game Mouse Logic ---
                if (button == GLFW_MOUSE_BUTTON_LEFT) {
                    if (action == GLFW_PRESS) {
                        isLeftMousePressed = true;
                        isLeftDraggingForPan = false;
                        uiHandledLeftMousePress = false;
                        pressMouseX = lastMouseX;
                        pressMouseY = lastMouseY;

                        if (game.isInventoryVisible()) {
                            int slotsPerRow = 5;
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

                            for (int i = 0; i < slots.size(); i++) {
                                if (mouseX_physical >= currentSlotX && mouseX_physical <= currentSlotX + slotSize &&
                                        mouseY_physical >= currentSlotY && mouseY_physical <= currentSlotY + slotSize) {
                                    game.setSelectedInventorySlotIndex(i);
                                    uiHandledLeftMousePress = true;
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
                            if (!uiHandledLeftMousePress) {
                                game.setSelectedInventorySlotIndex(-1);
                            }
                        }
                    } else if (action == GLFW_RELEASE) {
                        if (!isLeftDraggingForPan && !uiHandledLeftMousePress) {
                            if (camera != null && map != null && inputHandler != null && player != null && pathfinder != null) {
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
                        uiHandledLeftMousePress = false;
                    }
                } else if (button == GLFW_MOUSE_BUTTON_RIGHT && action == GLFW_RELEASE) {
                    int selectedSlot = game.getSelectedInventorySlotIndex();
                    Item itemToPlace = player.getPlaceableItemInSlot(selectedSlot);

                    if (itemToPlace != null) {
                        int[] targetCoords = camera.screenToAccurateMapTile(mouseX_physical, mouseY_physical, map);
                        if (targetCoords != null) {
                            int targetC = targetCoords[0];
                            int targetR = targetCoords[1];
                            int playerR = player.getTileRow();
                            int playerC = player.getTileCol();
                            int distance = Math.abs(targetR - playerR) + Math.abs(targetC - playerC);

                            if (distance <= MAX_INTERACTION_DISTANCE + 1) {
                                if (map.placeBlock(targetR, targetC, itemToPlace)) {
                                    if (!player.consumeItemFromSlot(selectedSlot, 1)) {
                                        System.err.println("Placed block but failed to consume item! Reverting (TODO).");
                                    }
                                }
                            } else {
                                System.out.println("Target tile for placement is too far away.");
                            }
                        }
                    } else {
                        System.out.println("No placeable item selected or slot is empty for right-click placement.");
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