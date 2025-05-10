package org.isogame.input;

import org.isogame.camera.CameraManager;
import org.isogame.entity.PlayerModel;
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

    public int selectedRow = 0;
    public int selectedCol = 0;
    private final Set<Integer> keysDown = new HashSet<>();

    public InputHandler(long window, CameraManager cameraManager, Map map, PlayerModel player) {
        this.window = window;
        this.cameraManager = cameraManager;
        this.map = map;
        this.player = player;
        this.selectedRow = player.getTileRow();
        this.selectedCol = player.getTileCol();
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
                    player.setPosition(map.getCharacterSpawnRow(), map.getCharacterSpawnCol());
                    player.setPath(null); // Clear path on map regeneration
                    selectedRow = player.getTileRow(); selectedCol = player.getTileCol();
                    cameraManager.setTargetPositionInstantly(player.getMapCol(), player.getMapRow());
                }
                break;
            case GLFW_KEY_F: player.toggleLevitate(); break;
            case GLFW_KEY_C: cameraManager.setTargetPosition(player.getMapCol(), player.getMapRow()); break;
            case GLFW_KEY_Q: modifySelectedTileElevation(-1); break;
            case GLFW_KEY_E: modifySelectedTileElevation(1); break;
            case GLFW_KEY_J: performDigAction(); break;
        }
    }

    // Player movement is now handled by pathfinding triggered by MouseHandler.
    // This method can be used for other continuous inputs, like camera panning with keys.
    public void handleContinuousInput(double deltaTime) {
        // Example: Camera Panning with arrow keys (if you want to keep this)
        float camPanSpeed = CAMERA_PAN_SPEED * (float)deltaTime;

        // Use the field name "cameraManager"
        if (keysDown.contains(GLFW_KEY_UP)) {
            cameraManager.moveTargetPosition(0, -camPanSpeed / cameraManager.getEffectiveTileHeight());
        }
        if (keysDown.contains(GLFW_KEY_DOWN)) {
            cameraManager.moveTargetPosition(0, camPanSpeed / cameraManager.getEffectiveTileHeight());
        }
        if (keysDown.contains(GLFW_KEY_LEFT)) {
            cameraManager.moveTargetPosition(-camPanSpeed / cameraManager.getEffectiveTileWidth(), 0);
        }
        if (keysDown.contains(GLFW_KEY_RIGHT)) {
            cameraManager.moveTargetPosition(camPanSpeed / cameraManager.getEffectiveTileWidth(), 0);
        }
    }

    private void performDigAction() {
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
            if (targetTile != null && targetTile.getElevation() >= NIVEL_MAR) { // Can only dig land (not below NIVEL_MAR essentially)
                Tile.TileType originalType = targetTile.getType();
                int originalElevation = targetTile.getElevation();
                int newElevation = originalElevation - 1;
                if (newElevation < 0) newElevation = 0;

                map.setTileElevation(targetR, targetC, newElevation);

                switch (originalType) {
                    case GRASS: player.addResource(RESOURCE_DIRT, 1); break;
                    case SAND: player.addResource(RESOURCE_SAND, 1); break;
                    case ROCK: player.addResource(RESOURCE_STONE, 1); break;
                }
            }
        }
    }

    private void modifySelectedTileElevation(int amount) {
        Tile tile = map.getTile(selectedRow, selectedCol);
        if (tile != null) {
            int currentElevation = tile.getElevation();
            int newElevation = Math.max(0, Math.min(ALTURA_MAXIMA, currentElevation + amount));
            if (newElevation != currentElevation) {
                map.setTileElevation(selectedRow, selectedCol, newElevation);
            }
        }
    }

    public void setSelectedTile(int col, int row) { // Note: common to pass col, then row
        if (map.isValid(row, col)) {
            this.selectedRow = row;
            this.selectedCol = col;
        }
    }
    public int getSelectedRow() { return selectedRow; }
    public int getSelectedCol() { return selectedCol; }
}