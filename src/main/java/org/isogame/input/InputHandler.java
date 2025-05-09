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
                handleSingleKeyPress(key, onGenerateMap); // Handle non-movement instant actions
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
                    selectedRow = player.getTileRow(); selectedCol = player.getTileCol();
                    cameraManager.setTargetPositionInstantly(player.getMapCol(), player.getMapRow());
                }
                break;
            case GLFW_KEY_F: player.toggleLevitate(); break;
            case GLFW_KEY_C: cameraManager.setTargetPosition(player.getMapCol(), player.getMapRow()); break;
            case GLFW_KEY_Q: modifySelectedTileElevation(-1); break;
            case GLFW_KEY_E: modifySelectedTileElevation(1); break;
            // Removed arrow key selection to focus on player movement
        }
    }

    public void handleContinuousInput(double deltaTime) {
        float moveAmount = (float) (PLAYER_MOVE_SPEED * deltaTime);
        float dRow = 0, dCol = 0;
        boolean attemptedMove = false;
        PlayerModel.Direction intendedDirection = player.getCurrentDirection(); // Start with current

        // Determine intended direction and if a move key is pressed
        // This simplified logic prioritizes vertical, then horizontal.
        // For 8-directional, you'd check combinations.
        if (keysDown.contains(GLFW_KEY_W)) {
            dRow -= moveAmount;
            intendedDirection = PlayerModel.Direction.NORTH;
            attemptedMove = true;
        } else if (keysDown.contains(GLFW_KEY_S)) {
            dRow += moveAmount;
            intendedDirection = PlayerModel.Direction.SOUTH;
            attemptedMove = true;
        }

        if (keysDown.contains(GLFW_KEY_A)) {
            dCol -= moveAmount;
            if (!attemptedMove) { // If not already moving N/S, set direction to W
                intendedDirection = PlayerModel.Direction.WEST;
            } else { // Moving diagonally
                if (intendedDirection == PlayerModel.Direction.NORTH) { /* intendedDirection = PlayerModel.Direction.NORTH_WEST; */ } // TODO: Add diagonal enums/logic
                else if (intendedDirection == PlayerModel.Direction.SOUTH) { /* intendedDirection = PlayerModel.Direction.SOUTH_WEST; */ }
            }
            attemptedMove = true;
        } else if (keysDown.contains(GLFW_KEY_D)) {
            dCol += moveAmount;
            if (!attemptedMove) { // If not already moving N/S, set direction to E
                intendedDirection = PlayerModel.Direction.EAST;
            } else { // Moving diagonally
                if (intendedDirection == PlayerModel.Direction.NORTH) { /* intendedDirection = PlayerModel.Direction.NORTH_EAST; */ }
                else if (intendedDirection == PlayerModel.Direction.SOUTH) { /* intendedDirection = PlayerModel.Direction.SOUTH_EAST; */ }
            }
            attemptedMove = true;
        }

        player.setDirection(intendedDirection); // Always update facing direction based on input

        if (attemptedMove) {
            float nextRow = player.getMapRow() + dRow;
            float nextCol = player.getMapCol() + dCol;

            if (map.isValid(Math.round(nextRow), Math.round(nextCol))) {
                Tile targetTile = map.getTile(Math.round(nextRow), Math.round(nextCol));
                // Basic walkability: not water and not too steep (example: max 1 level step)
                Tile currentTile = map.getTile(player.getTileRow(), player.getTileCol());
                int currentElevation = (currentTile != null) ? currentTile.getElevation() : 0;
                int targetElevation = (targetTile != null) ? targetTile.getElevation() : currentElevation;

                if (targetTile != null && targetTile.getType() != Tile.TileType.WATER && Math.abs(targetElevation - currentElevation) <= 1) {
                    player.move(dRow, dCol);
                    cameraManager.setTargetPosition(player.getMapCol(), player.getMapRow());
                    player.setAction(PlayerModel.Action.WALK);
                } else {
                    player.setAction(PlayerModel.Action.IDLE); // Blocked, so idle
                }
            } else {
                player.setAction(PlayerModel.Action.IDLE); // Hit map boundary
            }
        } else {
            player.setAction(PlayerModel.Action.IDLE);
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

    public void setSelectedTile(int row, int col) {
        if (map.isValid(row, col)) {
            this.selectedRow = row;
            this.selectedCol = col;
        }
    }
    public int getSelectedRow() { return selectedRow; }
    public int getSelectedCol() { return selectedCol; }
}