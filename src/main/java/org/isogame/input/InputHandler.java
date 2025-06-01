package org.isogame.input;

import org.isogame.camera.CameraManager;
import org.isogame.entitiy.PlayerModel;
import org.isogame.game.Game;
import org.isogame.item.ItemRegistry;
import org.isogame.map.Map;
import org.isogame.tile.Tile;

import static org.lwjgl.glfw.GLFW.*;
import static org.isogame.constants.Constants.*;

import java.util.HashSet;
import java.util.Set;

public class InputHandler {

    private final long window;
    private final CameraManager cameraManager;
    private final Map map;
    private final PlayerModel player;
    private final Game gameInstance;

    public int selectedRow = 0;
    public int selectedCol = 0;
    private final Set<Integer> keysDown = new HashSet<>();

    public InputHandler(long window, CameraManager cameraManager, Map map, PlayerModel player, Game gameInstance) {
        this.window = window;
        this.cameraManager = cameraManager;
        this.map = map;
        this.player = player;
        this.gameInstance = gameInstance;
        if (player != null) {
            this.selectedRow = player.getTileRow();
            this.selectedCol = player.getTileCol();
        }
    }

    public void registerCallbacks(Runnable requestFullMapRegenerationCallback) {
        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
                if (gameInstance != null && gameInstance.getCurrentGameState() == Game.GameState.IN_GAME) {
                    // If in game, pressing Esc could go to Main Menu or a Pause Menu (future)
                    gameInstance.setCurrentGameState(Game.GameState.MAIN_MENU);
                    System.out.println("Escape pressed in-game, returning to Main Menu.");
                } else if (gameInstance != null && gameInstance.getCurrentGameState() == Game.GameState.MAIN_MENU) {
                    // If in main menu, Esc could exit the game
                    glfwSetWindowShouldClose(window, true);
                } else {
                    glfwSetWindowShouldClose(window, true); // Default exit
                }
                return;
            }
            if (action == GLFW_PRESS) {
                keysDown.add(key);
                handleSingleKeyPress(key, requestFullMapRegenerationCallback);
            } else if (action == GLFW_RELEASE) {
                keysDown.remove(key);
            }
        });
    }

    private void handleSingleKeyPress(int key, Runnable requestFullMapRegenerationCallback) {
        // Only process these if in game
        if (gameInstance != null && gameInstance.getCurrentGameState() == Game.GameState.IN_GAME) {
            switch (key) {
                case GLFW_KEY_G:
                    if (requestFullMapRegenerationCallback != null) {
                        requestFullMapRegenerationCallback.run();
                    }
                    break;
                case GLFW_KEY_F:
                    if (player != null) player.toggleLevitate();
                    break;
                case GLFW_KEY_C:
                    if (player != null && cameraManager != null) {
                        cameraManager.setTargetPosition(player.getMapCol(), player.getMapRow());
                    }
                    break;
                case GLFW_KEY_Q:
                    modifySelectedTileElevation(-1);
                    break;
                case GLFW_KEY_E:
                    modifySelectedTileElevation(1);
                    break;
                case GLFW_KEY_J:
                    performDigAction();
                    break;
                case GLFW_KEY_L:
                    if (map != null) {
                        map.toggleTorch(selectedRow, selectedCol);
                    }
                    break;
                case GLFW_KEY_F5:
                    gameInstance.toggleShowDebugOverlay();
                    break;
                case GLFW_KEY_F6:
                    gameInstance.decreaseRenderDistance();
                    break;
                case GLFW_KEY_F7:
                    gameInstance.increaseRenderDistance();
                    break;
                case GLFW_KEY_F9: // Save Game
                    String currentWorld = gameInstance.getCurrentWorldName();
                    if (currentWorld != null && !currentWorld.trim().isEmpty()) {
                        gameInstance.saveGame(currentWorld);
                    } else {
                        System.out.println("Cannot save: No current world loaded/named. Create or load a world first.");
                        // Optionally, trigger a "Save As" if UI supported it, or save to a default
                        // gameInstance.saveGame("default_unsaved_world");
                    }
                    break;
                case GLFW_KEY_I:
                    gameInstance.toggleInventory();
                    break;
                case GLFW_KEY_H: // Example: 'H' key to toggle hotbar
                    gameInstance.toggleHotbar();
                    break;

                // Hotbar Slot Selection (Keys 1-5 for slots 0-4)
                case GLFW_KEY_1: if (player != null) gameInstance.setSelectedInventorySlotIndex(0); break;
                case GLFW_KEY_2: if (player != null) gameInstance.setSelectedInventorySlotIndex(1); break;
                case GLFW_KEY_3: if (player != null) gameInstance.setSelectedInventorySlotIndex(2); break;
                case GLFW_KEY_4: if (player != null) gameInstance.setSelectedInventorySlotIndex(3); break;
                case GLFW_KEY_5: if (player != null) gameInstance.setSelectedInventorySlotIndex(4); break;
            }
        } else if (gameInstance != null && gameInstance.getCurrentGameState() == Game.GameState.MAIN_MENU) {
            // Handle any global keys for main menu if needed (e.g. number keys for menu options)
            // Currently, main menu interaction is mouse-driven in MouseHandler.
        }
    }

    public void handleContinuousInput(double deltaTime) {
        if (gameInstance != null && gameInstance.getCurrentGameState() == Game.GameState.IN_GAME) {
            if (cameraManager == null) return;
            float camPanSpeed = CAMERA_PAN_SPEED * (float) deltaTime;
            if (keysDown.contains(GLFW_KEY_UP)) cameraManager.moveTargetPosition(cameraManager.screenVectorToMapVector(0, -camPanSpeed)[0], cameraManager.screenVectorToMapVector(0, -camPanSpeed)[1]);
            if (keysDown.contains(GLFW_KEY_DOWN)) cameraManager.moveTargetPosition(cameraManager.screenVectorToMapVector(0, camPanSpeed)[0], cameraManager.screenVectorToMapVector(0, camPanSpeed)[1]);
            if (keysDown.contains(GLFW_KEY_LEFT)) cameraManager.moveTargetPosition(cameraManager.screenVectorToMapVector(-camPanSpeed, 0)[0], cameraManager.screenVectorToMapVector(-camPanSpeed, 0)[1]);
            if (keysDown.contains(GLFW_KEY_RIGHT)) cameraManager.moveTargetPosition(cameraManager.screenVectorToMapVector(camPanSpeed, 0)[0], cameraManager.screenVectorToMapVector(camPanSpeed, 0)[1]);
        }
    }

    private void performDigAction() {
        if (player == null || map == null || gameInstance == null) return;
        int playerR = player.getTileRow();
        int playerC = player.getTileCol();
        PlayerModel.Direction facingDir = player.getCurrentDirection();
        int targetR = playerR, targetC = playerC;

        switch (facingDir) {
            case NORTH: targetR--; break;
            case SOUTH: targetR++; break;
            case WEST:  targetC--; break;
            case EAST:  targetC++; break;
        }

        if (Math.abs(targetR - playerR) + Math.abs(targetC - playerC) > MAX_INTERACTION_DISTANCE) return;

        if (map.isValid(targetR, targetC)) {
            Tile targetTile = map.getTile(targetR, targetC);
            if (targetTile != null && targetTile.getElevation() >= NIVEL_MAR) {
                Tile.TileType originalType = targetTile.getType();
                int originalElevation = targetTile.getElevation();
                int newElevation = Math.max(0, originalElevation - 1);

                if (originalElevation != newElevation) {
                    map.setTileElevation(targetR, targetC, newElevation);
                    switch (originalType) {
                        case GRASS: player.addItemToInventory(ItemRegistry.DIRT, 1); break;
                        case SAND: player.addItemToInventory(ItemRegistry.SAND, 1); break;
                        case ROCK: player.addItemToInventory(ItemRegistry.STONE, 1); break;
                    }
                }
            }
        }
    }

    private void modifySelectedTileElevation(int amount) {
        if (map == null || gameInstance == null) return;
        Tile tile = map.getTile(selectedRow, selectedCol);
        if (tile != null) {
            int currentElevation = tile.getElevation();
            int newElevation = Math.max(0, Math.min(ALTURA_MAXIMA, currentElevation + amount));
            if (newElevation != currentElevation) {
                map.setTileElevation(selectedRow, selectedCol, newElevation);
            }
        }
    }

    public void setSelectedTile(int col, int row) {
        if (map == null || !map.isValid(row, col)) return;
        int oldSelectedRow = this.selectedRow;
        int oldSelectedCol = this.selectedCol;
        this.selectedRow = row;
        this.selectedCol = col;
        if (gameInstance != null) {
            if (map.isValid(oldSelectedRow, oldSelectedCol)) {
                gameInstance.requestTileRenderUpdate(oldSelectedRow, oldSelectedCol);
            }
            gameInstance.requestTileRenderUpdate(this.selectedRow, this.selectedCol);
        }
    }

    public Game getGameInstance() {
        return this.gameInstance;
    }

    public int getSelectedRow() { return selectedRow; }
    public int getSelectedCol() { return selectedCol; }
}