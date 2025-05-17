// InputHandler.java
package org.isogame.input;

import org.isogame.camera.CameraManager;
import org.isogame.entitiy.PlayerModel;
import org.isogame.game.Game;
import org.isogame.map.Map;
import org.isogame.tile.Tile;

import static org.lwjgl.glfw.GLFW.*;
import static org.isogame.constants.Constants.*; // Ensure this line imports all static fields from Constants

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
        if (player != null) {
            this.selectedRow = player.getTileRow();
            this.selectedCol = player.getTileCol();
        } else {
            this.selectedRow = 0;
            this.selectedCol = 0;
        }
        this.gameInstance = gameInstance;
    }

    public void registerCallbacks(Runnable onGenerateMap) {
        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
                glfwSetWindowShouldClose(window, true);
                return;
            }
            if (action == GLFW_PRESS) {
                keysDown.add(key);
                handleSingleKeyPress(key, onGenerateMap);
            } else if (action == GLFW_RELEASE) {
                keysDown.remove(key);
            }
        });
    }

    private void handleSingleKeyPress(int key, Runnable onGenerateMap) {
        switch (key) {
            case GLFW_KEY_G:
                if (onGenerateMap != null) {
                    onGenerateMap.run();
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
        }
    }

    public void handleContinuousInput(double deltaTime) {
        if (cameraManager == null) return;

        float camPanSpeed = CAMERA_PAN_SPEED * (float) deltaTime;
        float effectiveTileHeight = cameraManager.getEffectiveTileHeight();
        float effectiveTileWidth = cameraManager.getEffectiveTileWidth();

        if (keysDown.contains(GLFW_KEY_UP)) {
            cameraManager.moveTargetPosition(0, -camPanSpeed / (effectiveTileHeight != 0 ? effectiveTileHeight : TILE_HEIGHT));
        }
        if (keysDown.contains(GLFW_KEY_DOWN)) {
            cameraManager.moveTargetPosition(0, camPanSpeed / (effectiveTileHeight != 0 ? effectiveTileHeight : TILE_HEIGHT));
        }
        if (keysDown.contains(GLFW_KEY_LEFT)) {
            cameraManager.moveTargetPosition(-camPanSpeed / (effectiveTileWidth != 0 ? effectiveTileWidth : TILE_WIDTH), 0);
        }
        if (keysDown.contains(GLFW_KEY_RIGHT)) {
            cameraManager.moveTargetPosition(camPanSpeed / (effectiveTileWidth != 0 ? effectiveTileWidth : TILE_WIDTH), 0);
        }
    }

    private void performDigAction() {
        if (player == null || map == null || gameInstance == null) return;

        int playerR = player.getTileRow();
        int playerC = player.getTileCol();
        PlayerModel.Direction facingDir = player.getCurrentDirection();
        int targetR = playerR;
        int targetC = playerC;

        switch (facingDir) {
            case NORTH: targetR -= MAX_INTERACTION_DISTANCE; break;
            case SOUTH: targetR += MAX_INTERACTION_DISTANCE; break;
            case WEST:  targetC -= MAX_INTERACTION_DISTANCE; break;
            case EAST:  targetC += MAX_INTERACTION_DISTANCE; break;
        }

        if (map.isValid(targetR, targetC)) {
            Tile targetTile = map.getTile(targetR, targetC);
            if (targetTile != null && targetTile.getElevation() >= NIVEL_MAR) {
                Tile.TileType originalType = targetTile.getType();
                int originalElevation = targetTile.getElevation();
                int newElevation = originalElevation - 1;
                if (newElevation < 0) newElevation = 0;

                if (originalElevation != newElevation) {
                    map.setTileElevation(targetR, targetC, newElevation);
                    this.gameInstance.requestTileRenderUpdate(targetR, targetC);

                    switch (originalType) {
                        case GRASS: player.addResource(RESOURCE_DIRT, 1); break;
                        case SAND: player.addResource(RESOURCE_SAND, 1); break;
                        case ROCK: player.addResource(RESOURCE_STONE, 1); break;
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
                this.gameInstance.requestTileRenderUpdate(selectedRow, selectedCol);
            }
        }
    }

    public void setSelectedTile(int col, int row) {
        if (map == null || !map.isValid(row, col)) {
            return;
        }

        int oldSelectedRow = this.selectedRow;
        int oldSelectedCol = this.selectedCol;

        boolean selectionActuallyChanged = (this.selectedRow != row || this.selectedCol != col);

        this.selectedRow = row;
        this.selectedCol = col;

        if (selectionActuallyChanged && this.gameInstance != null) {
            // Determine chunk coordinates for old and new selections
            // *** CHUNK_SIZE_TILES is used here ***
            int oldChunkR = oldSelectedRow / CHUNK_SIZE_TILES; // Make sure CHUNK_SIZE_TILES is imported from Constants
            int oldChunkC = oldSelectedCol / CHUNK_SIZE_TILES; // Make sure CHUNK_SIZE_TILES is imported from Constants
            int newChunkR = this.selectedRow / CHUNK_SIZE_TILES; // Make sure CHUNK_SIZE_TILES is imported from Constants
            int newChunkC = this.selectedCol / CHUNK_SIZE_TILES; // Make sure CHUNK_SIZE_TILES is imported from Constants

            if (map.isValid(oldSelectedRow, oldSelectedCol)) {
                System.out.println("InputHandler: Requesting update for previously selected tile's chunk: R" + oldSelectedRow + ", C" + oldSelectedCol);
                this.gameInstance.requestTileRenderUpdate(oldSelectedRow, oldSelectedCol);
            }

            if (oldChunkR != newChunkR || oldChunkC != newChunkC || (oldSelectedRow != this.selectedRow || oldSelectedCol != this.selectedCol)) {
                System.out.println("InputHandler: Requesting update for newly selected tile's chunk: R" + this.selectedRow + ", C" + this.selectedCol);
                this.gameInstance.requestTileRenderUpdate(this.selectedRow, this.selectedCol);
            }
        }
    }

    public int getSelectedRow() {
        return selectedRow;
    }

    public int getSelectedCol() {
        return selectedCol;
    }
}