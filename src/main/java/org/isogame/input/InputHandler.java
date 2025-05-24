package org.isogame.input;

import org.isogame.camera.CameraManager;
import org.isogame.entitiy.PlayerModel;
import org.isogame.game.Game;
import org.isogame.map.Map;
// import org.isogame.map.ChunkManager; // Not directly needed here after changes
// import org.isogame.map.LightManager; // Not directly needed here after changes
import org.isogame.tile.Tile;

import static org.lwjgl.glfw.GLFW.*;
import static org.isogame.constants.Constants.*;

import java.util.HashSet;
import java.util.Set;

public class InputHandler {

    private final long window;
    private final CameraManager cameraManager;
    private final Map map; // Reference to the game map for tile interactions
    private final PlayerModel player; // Reference to the player model for actions
    private final Game gameInstance; // Reference to the main game instance for global actions

    public int selectedRow = 0; // Currently selected tile row by input
    public int selectedCol = 0; // Currently selected tile column by input
    private final Set<Integer> keysDown = new HashSet<>(); // Tracks currently pressed keys for continuous input

    /**
     * Constructor for InputHandler.
     * @param window GLFW window handle.
     * @param cameraManager Reference to the camera manager.
     * @param map Reference to the game map.
     * @param player Reference to the player model.
     * @param gameInstance Reference to the game instance.
     */
    public InputHandler(long window, CameraManager cameraManager, Map map, PlayerModel player, Game gameInstance) {
        this.window = window;
        this.cameraManager = cameraManager;
        this.map = map;
        this.player = player;
        this.gameInstance = gameInstance;
        if (player != null) { // Initialize selected tile to player's starting position
            this.selectedRow = player.getTileRow();
            this.selectedCol = player.getTileCol();
        }
    }

    /**
     * Registers GLFW key callback.
     * @param requestFullMapRegenerationCallback Callback function to trigger a full map regeneration.
     */
    public void registerCallbacks(Runnable requestFullMapRegenerationCallback) {
        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
                glfwSetWindowShouldClose(window, true); // Close window on ESC
                return;
            }
            if (action == GLFW_PRESS) {
                keysDown.add(key); // Add to set of pressed keys
                handleSingleKeyPress(key, requestFullMapRegenerationCallback); // Handle discrete key presses
            } else if (action == GLFW_RELEASE) {
                keysDown.remove(key); // Remove from set of pressed keys
            }
        });
    }

    /**
     * Handles discrete key press events for one-off actions.
     * @param key The GLFW key code.
     * @param requestFullMapRegenerationCallback Callback for map regeneration.
     */
    private void handleSingleKeyPress(int key, Runnable requestFullMapRegenerationCallback) {
        switch (key) {
            case GLFW_KEY_G: // Regenerate map
                if (requestFullMapRegenerationCallback != null) {
                    requestFullMapRegenerationCallback.run();
                }
                break;
            case GLFW_KEY_F: // Toggle player levitation (debug)
                if (player != null) player.toggleLevitate();
                break;
            case GLFW_KEY_C: // Center camera on player
                if (player != null && cameraManager != null) {
                    cameraManager.setTargetPosition(player.getMapCol(), player.getMapRow());
                }
                break;
            case GLFW_KEY_Q: // Decrease selected tile elevation
                modifySelectedTileElevation(-1);
                break;
            case GLFW_KEY_E: // Increase selected tile elevation
                modifySelectedTileElevation(1);
                break;
            case GLFW_KEY_J: // Perform dig action
                performDigAction();
                break;
            case GLFW_KEY_L: // Toggle torch at selected tile
                if (map != null) {
                    map.toggleTorch(selectedRow, selectedCol);
                }
                break;
            case GLFW_KEY_F5: // Toggle debug overlay
                if (gameInstance != null) {
                    gameInstance.toggleShowDebugOverlay();
                }
                break;
        }
    }

    /**
     * Handles continuous input for actions that occur while a key is held down (e.g., camera panning).
     * @param deltaTime Time elapsed since the last frame.
     */
    public void handleContinuousInput(double deltaTime) {
        if (cameraManager == null) return;
        float camPanSpeed = CAMERA_PAN_SPEED * (float) deltaTime; // Pan speed adjusted for delta time
        // Camera panning with arrow keys
        if (keysDown.contains(GLFW_KEY_UP)) cameraManager.moveTargetPosition(cameraManager.screenVectorToMapVector(0, -camPanSpeed)[0], cameraManager.screenVectorToMapVector(0, -camPanSpeed)[1]);
        if (keysDown.contains(GLFW_KEY_DOWN)) cameraManager.moveTargetPosition(cameraManager.screenVectorToMapVector(0, camPanSpeed)[0], cameraManager.screenVectorToMapVector(0, camPanSpeed)[1]);
        if (keysDown.contains(GLFW_KEY_LEFT)) cameraManager.moveTargetPosition(cameraManager.screenVectorToMapVector(-camPanSpeed, 0)[0], cameraManager.screenVectorToMapVector(-camPanSpeed, 0)[1]);
        if (keysDown.contains(GLFW_KEY_RIGHT)) cameraManager.moveTargetPosition(cameraManager.screenVectorToMapVector(camPanSpeed, 0)[0], cameraManager.screenVectorToMapVector(camPanSpeed, 0)[1]);
    }

    /**
     * Performs a dig action at the tile in front of the player.
     */
    private void performDigAction() {
        if (player == null || map == null || gameInstance == null) return;
        int playerR = player.getTileRow();
        int playerC = player.getTileCol();
        PlayerModel.Direction facingDir = player.getCurrentDirection();
        int targetR = playerR, targetC = playerC;

        // Determine target tile based on player direction
        switch (facingDir) {
            case NORTH: targetR--; break;
            case SOUTH: targetR++; break;
            case WEST:  targetC--; break;
            case EAST:  targetC++; break;
        }

        // Check interaction distance
        if (Math.abs(targetR - playerR) + Math.abs(targetC - playerC) > MAX_INTERACTION_DISTANCE) return;

        if (map.isValid(targetR, targetC)) {
            Tile targetTile = map.getTile(targetR, targetC);
            if (targetTile != null && targetTile.getElevation() >= NIVEL_MAR) { // Can only dig non-water tiles
                Tile.TileType originalType = targetTile.getType();
                int originalElevation = targetTile.getElevation();
                int newElevation = Math.max(0, originalElevation - 1); // Dig down one level

                if (originalElevation != newElevation) {
                    map.setTileElevation(targetR, targetC, newElevation); // Update map

                    // Add resource to player inventory based on dug tile type
                    switch (originalType) {
                        case GRASS: player.addResource(RESOURCE_DIRT, 1); break;
                        case SAND: player.addResource(RESOURCE_SAND, 1); break;
                        case ROCK: player.addResource(RESOURCE_STONE, 1); break;
                        default: // No resource for other types like SNOW or if type changes to WATER
                            break;
                    }
                    // The call to map.setTileElevation will handle marking the chunk dirty for lighting and mesh rebuild.
                }
            }
        }
    }

    /**
     * Modifies the elevation of the currently selected tile.
     * @param amount The amount to change the elevation by (+1 or -1).
     */
    private void modifySelectedTileElevation(int amount) {
        if (map == null || gameInstance == null) return;
        Tile tile = map.getTile(selectedRow, selectedCol);
        if (tile != null) {
            int currentElevation = tile.getElevation();
            int newElevation = Math.max(0, Math.min(ALTURA_MAXIMA, currentElevation + amount)); // Clamp elevation
            if (newElevation != currentElevation) {
                map.setTileElevation(selectedRow, selectedCol, newElevation);
                // map.setTileElevation handles lighting updates and marking chunk dirty.
            }
        }
    }

    /**
     * Sets the currently selected tile coordinates.
     * This method NO LONGER marks chunks dirty for lighting, as selection is a visual renderer concern.
     * @param col The new selected column.
     * @param row The new selected row.
     */
    public void setSelectedTile(int col, int row) {
        // int oldSelectedRow = this.selectedRow; // Keep if needed for other logic
        // int oldSelectedCol = this.selectedCol;
        this.selectedRow = row;
        this.selectedCol = col;

        // PERFORMANCE FIX: Removing the automatic marking of chunks as dirty for lighting.
        // The renderer will handle the visual selection highlight when it rebuilds
        // a chunk's mesh for other reasons (e.g. actual terrain change, initial load).
        // If selection itself needs to force an immediate visual update without terrain change,
        // a more lightweight mechanism in the renderer would be needed (e.g., shader uniform or
        // specific VBO update for just the selected tile's color/tint attribute).

        // Example: If you wanted to force a redraw of the specific chunks (old and new selected)
        // without a full lighting recalc, you might do:
        // if (gameInstance != null && map != null && map.getChunkManager() != null && renderer != null) {
        //     ChunkManager chunkManager = map.getChunkManager();
        //     if (chunkManager.getLoadedChunk(Map.worldToChunkCoord(oldSelectedCol), Map.worldToChunkCoord(oldSelectedRow)) != null) {
        //         renderer.updateRenderChunk(new ChunkCoordinate(Map.worldToChunkCoord(oldSelectedCol), Map.worldToChunkCoord(oldSelectedRow)));
        //     }
        //     if (chunkManager.getLoadedChunk(Map.worldToChunkCoord(this.selectedCol), Map.worldToChunkCoord(this.selectedRow)) != null) {
        //         renderer.updateRenderChunk(new ChunkCoordinate(Map.worldToChunkCoord(this.selectedCol), Map.worldToChunkCoord(this.selectedRow)));
        //     }
        // }
    }

    // Getters for selected tile coordinates
    public int getSelectedRow() { return selectedRow; }
    public int getSelectedCol() { return selectedCol; }
}
