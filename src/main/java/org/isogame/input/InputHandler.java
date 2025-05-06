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

    // Tile selection state
    public int selectedRow = 0;
    public int selectedCol = 0;

    // Keep track of held keys for smooth movement/actions
    private final Set<Integer> keysDown = new HashSet<>();

    public InputHandler(long window, CameraManager cameraManager, Map map, PlayerModel player) {
        this.window = window;
        this.cameraManager = cameraManager;
        this.map = map;
        this.player = player;

        // Initialize selection to player start position
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
                handleKeyPress(key, onGenerateMap);
            } else if (action == GLFW_RELEASE) {
                keysDown.remove(key);
            }
        });

        // We might not need a repeat callback if we handle continuous input separately
        // glfwSetKeyCallback handles PRESS and RELEASE which is usually sufficient with the keysDown set
    }

    // Handle instant actions on key press
    private void handleKeyPress(int key, Runnable onGenerateMap) {
        switch (key) {
            case GLFW_KEY_G: // Regenerate Map
                if (onGenerateMap != null) {
                    onGenerateMap.run();
                    // Reset player/camera to new spawn point after regeneration
                    player.setPosition(map.getCharacterSpawnRow(), map.getCharacterSpawnCol());
                    selectedRow = player.getTileRow();
                    selectedCol = player.getTileCol();
                    cameraManager.setTargetPositionInstantly(player.getMapCol(), player.getMapRow());
                }
                break;
            case GLFW_KEY_F: // Toggle Levitate
                player.toggleLevitate();
                break;
            case GLFW_KEY_C: // Center Camera on Player
                cameraManager.setTargetPosition(player.getMapCol(), player.getMapRow());
                break;
            case GLFW_KEY_Q: // Modify selected tile height (Example action)
                modifySelectedTileElevation(-1);
                break;
            case GLFW_KEY_E: // Modify selected tile height (Example action)
                modifySelectedTileElevation(1);
                break;
            // Arrow keys can select tile (optional) - maybe better for mouse?
            case GLFW_KEY_UP: selectedRow = Math.max(0, selectedRow - 1); break;
            case GLFW_KEY_DOWN: selectedRow = Math.min(map.getHeight() - 1, selectedRow + 1); break;
            case GLFW_KEY_LEFT: selectedCol = Math.max(0, selectedCol - 1); break;
            case GLFW_KEY_RIGHT: selectedCol = Math.min(map.getWidth() - 1, selectedCol + 1); break;

        }
    }

    // Handle continuous actions for keys held down (called from game loop)
    public void handleContinuousInput(double deltaTime) {
        // --- Player Movement ---
        float moveAmount = (float) (PLAYER_MOVE_SPEED * deltaTime);
        float dRow = 0, dCol = 0;
        boolean moved = false;

        if (keysDown.contains(GLFW_KEY_W)) { dRow -= moveAmount; moved = true; }
        if (keysDown.contains(GLFW_KEY_S)) { dRow += moveAmount; moved = true; }
        if (keysDown.contains(GLFW_KEY_A)) { dCol -= moveAmount; moved = true; }
        if (keysDown.contains(GLFW_KEY_D)) { dCol += moveAmount; moved = true; }

        if (moved) {
            // Basic collision detection/boundary check before moving
            float nextRow = player.getMapRow() + dRow;
            float nextCol = player.getMapCol() + dCol;

            // Check bounds roughly
            if (nextRow >= 0 && nextRow < map.getHeight() && nextCol >= 0 && nextCol < map.getWidth()) {
                // Very simple: allow movement only on non-water tiles (more complex checks needed for height differences)
                Tile targetTile = map.getTile(Math.round(nextRow), Math.round(nextCol));
                if (targetTile != null && targetTile.getType() != Tile.TileType.WATER) {
                    player.move(dRow, dCol);
                    cameraManager.setTargetPosition(player.getMapCol(), player.getMapRow()); // Make camera follow player
                }
            }
        }

        // --- Camera Panning (Example - can be removed if camera always follows player) ---
//        float camPanAmount = (float) (CAMERA_PAN_SPEED * deltaTime / cameraManager.getZoom()); // Adjust pan speed by zoom
//        if (keysDown.contains(GLFW_KEY_UP)) cameraManager.moveTargetPosition(0, -camPanAmount);
//        if (keysDown.contains(GLFW_KEY_DOWN)) cameraManager.moveTargetPosition(0, camPanAmount);
//        if (keysDown.contains(GLFW_KEY_LEFT)) cameraManager.moveTargetPosition(-camPanAmount, 0);
//        if (keysDown.contains(GLFW_KEY_RIGHT)) cameraManager.moveTargetPosition(camPanAmount, 0);
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

    // Called by MouseHandler to update the selected tile
    public void setSelectedTile(int row, int col) {
        if (map.isValid(row, col)) {
            this.selectedRow = row;
            this.selectedCol = col;
        }
    }

    public int getSelectedRow() {
        return selectedRow;
    }

    public int getSelectedCol() {
        return selectedCol;
    }
}