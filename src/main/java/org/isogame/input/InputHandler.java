package org.isogame.input;

import org.isogame.camera.CameraManager;
import org.isogame.constants.Constants;
import org.isogame.entitiy.PlayerModel;
import org.isogame.game.Game;
import org.isogame.inventory.InventorySlot;
import org.isogame.item.Item;
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

    private static final float PLAYER_CAMERA_Y_SCREEN_OFFSET = -75.0f; // Visual offset for camera focus

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
                    gameInstance.setCurrentGameState(Game.GameState.MAIN_MENU);
                } else if (gameInstance != null && gameInstance.getCurrentGameState() == Game.GameState.MAIN_MENU) {
                    glfwSetWindowShouldClose(window, true);
                } else { // Should not happen, but as a fallback
                    glfwSetWindowShouldClose(window, true);
                }
                return;
            }

            // Handle key presses relevant only when in game
            if (gameInstance != null && gameInstance.getCurrentGameState() == Game.GameState.IN_GAME) {
                if (action == GLFW_PRESS) {
                    keysDown.add(key);
                    handleSingleKeyPressInGame(key, requestFullMapRegenerationCallback); // Renamed for clarity
                } else if (action == GLFW_RELEASE) {
                    keysDown.remove(key);
                    // Player movement input reset
                    if ((key == GLFW_KEY_W || key == GLFW_KEY_S || key == GLFW_KEY_A || key == GLFW_KEY_D) && player != null) {
                        boolean stillPressingMovementKey = keysDown.stream().anyMatch(k ->
                                k == GLFW_KEY_W || k == GLFW_KEY_S || k == GLFW_KEY_A || k == GLFW_KEY_D);
                        if (!stillPressingMovementKey) {
                            player.setMovementInput(0f, 0f);
                            // PlayerModel.update will set Action to IDLE if it was WALK and no input
                        }
                    }
                }
            } else if (action == GLFW_PRESS) { // Handle global key presses even if not in game (e.g., for menu)
                keysDown.add(key); // Still track keys for potential menu interactions later
            } else if (action == GLFW_RELEASE) {
                keysDown.remove(key);
            }
        });
    }

    // Renamed to clarify it's for in-game actions
    private void handleSingleKeyPressInGame(int key, Runnable requestFullMapRegenerationCallback) {
        // This method is only called if gameInstance.getCurrentGameState() == Game.GameState.IN_GAME
        switch (key) {
            case GLFW_KEY_J:
                performPlayerActionOnCurrentlySelectedTile();
                break;
            case GLFW_KEY_C:
                if (player != null && cameraManager != null && map != null) {
                    cameraManager.stopManualPan(); // Stop any manual panning
                    // Recalculate focus point based on current player position
                    float[] focusPoint = calculateCameraFocusPoint(player.getMapCol(), player.getMapRow());
                    cameraManager.setTargetPositionInstantly(focusPoint[0], focusPoint[1]);
                }
                break;
            case GLFW_KEY_G: if (requestFullMapRegenerationCallback != null) requestFullMapRegenerationCallback.run(); break;
            case GLFW_KEY_F: if (player != null) player.toggleLevitate(); break;
            case GLFW_KEY_Q: modifySelectedTileElevation(-1); break;
            case GLFW_KEY_E: modifySelectedTileElevation(1); break;
            case GLFW_KEY_L: if (map != null) map.toggleTorch(selectedRow, selectedCol); break;
            case GLFW_KEY_H: if(gameInstance != null) gameInstance.toggleHotbar(); break; // Ensure gameInstance check
            case GLFW_KEY_F5: if(gameInstance != null) gameInstance.toggleShowDebugOverlay(); break;
            case GLFW_KEY_F6: if(gameInstance != null) gameInstance.decreaseRenderDistance(); break;
            case GLFW_KEY_F7: if(gameInstance != null) gameInstance.increaseRenderDistance(); break;
            case GLFW_KEY_F9:
                if (gameInstance != null) {
                    String currentWorld = gameInstance.getCurrentWorldName();
                    if (currentWorld != null && !currentWorld.trim().isEmpty()) {
                        gameInstance.saveGame(currentWorld);
                    } else {
                        System.out.println("InputHandler: Cannot save, no current world name set in Game.");
                        // Optionally, prompt for a name or save to a default if desired.
                    }
                }
                break;
            case GLFW_KEY_I: if(gameInstance != null) gameInstance.toggleInventory(); break;
            // Hotbar selection keys
            case GLFW_KEY_1: if(gameInstance != null) gameInstance.setSelectedInventorySlotIndex(0); break;
            case GLFW_KEY_2: if(gameInstance != null) gameInstance.setSelectedInventorySlotIndex(1); break;
            case GLFW_KEY_3: if(gameInstance != null) gameInstance.setSelectedInventorySlotIndex(2); break;
            case GLFW_KEY_4: if(gameInstance != null) gameInstance.setSelectedInventorySlotIndex(3); break;
            case GLFW_KEY_5: if(gameInstance != null) gameInstance.setSelectedInventorySlotIndex(4); break;
            // Add more for larger hotbars if Constants.HOTBAR_SIZE allows
            case GLFW_KEY_6: if(gameInstance != null && Constants.HOTBAR_SIZE > 5) gameInstance.setSelectedInventorySlotIndex(5); break;
            case GLFW_KEY_7: if(gameInstance != null && Constants.HOTBAR_SIZE > 6) gameInstance.setSelectedInventorySlotIndex(6); break;
            case GLFW_KEY_8: if(gameInstance != null && Constants.HOTBAR_SIZE > 7) gameInstance.setSelectedInventorySlotIndex(7); break;
            case GLFW_KEY_9: if(gameInstance != null && Constants.HOTBAR_SIZE > 8) gameInstance.setSelectedInventorySlotIndex(8); break;
            // GLFW_KEY_0 maps to slot 9 (index) if hotbar is size 10
            case GLFW_KEY_0: if(gameInstance != null && Constants.HOTBAR_SIZE > 9) gameInstance.setSelectedInventorySlotIndex(9); break;
        }
    }

    // Made public so Game class can call it
    public float[] calculateCameraFocusPoint(float playerMapCol, float playerMapRow) {
        float targetFocusCol = playerMapCol;
        float targetFocusRow = playerMapRow;

        if (player == null || cameraManager == null || map == null) {
            // Return player's direct map coordinates if essential components are missing
            return new float[]{playerMapCol, playerMapRow};
        }

        int playerElevation = 0;
        Tile playerTile = map.getTile(player.getTileRow(), player.getTileCol());
        if (playerTile != null) {
            playerElevation = playerTile.getElevation();
        }

        // Calculate the Y offset in world space to center the player's visual representation
        // This considers player sprite height and the desired screen offset.
        float playerVisualCenterWorldYOffset = (-playerElevation * TILE_THICKNESS) - (PLAYER_WORLD_RENDER_HEIGHT / 2.0f);
        float totalWorldYOffsetToCenterPlayerVisually = playerVisualCenterWorldYOffset - (PLAYER_CAMERA_Y_SCREEN_OFFSET / cameraManager.getZoom());

        // Convert this world Y offset back into map grid units.
        // In isometric projection: worldY = (mapCol + mapRow) * TILE_HEIGHT_HALF
        // So, delta_worldY = (delta_mapCol + delta_mapRow) * TILE_HEIGHT_HALF
        // If we want to shift focus equally in mapCol and mapRow: delta_mapCol = delta_mapRow = D
        // delta_worldY = 2 * D * TILE_HEIGHT_HALF = D * TILE_HEIGHT
        // D = delta_worldY / TILE_HEIGHT
        // However, our camera moves by adjusting targetX (mapCol) and targetY (mapRow) for the *center* of the screen.
        // The translation (-camVisualX, -camVisualY, 0) in updateViewMatrix uses:
        // camVisualX = (cameraX - cameraY) * TILE_WIDTH_HALF
        // camVisualY = (cameraX + cameraY) * TILE_HEIGHT_HALF
        // We want to adjust cameraX and cameraY (our targetFocusCol, targetFocusRow)
        // such that the player appears at a certain screen position.
        // The offset `totalWorldYOffsetToCenterPlayerVisually` is in the direction of positive screen Y.
        // An increase in mapRow moves the world "down-left" on screen (positive screen Y for iso Y part).
        // An increase in mapCol moves the world "down-right" on screen (positive screen Y for iso Y part).
        // So, to compensate for a positive `totalWorldYOffsetToCenterPlayerVisually` (player is too high on screen,
        // or rather, camera is looking too "low" on the map), we need to increase both mapCol and mapRow components of camera target.
        // delta_map_units_sum = totalWorldYOffsetToCenterPlayerVisually / (TILE_HEIGHT / 2.0f)
        // delta_map_col = delta_map_units_sum / 2.0f
        // delta_map_row = delta_map_units_sum / 2.0f

        float deltaMapUnitsSumForCentering = totalWorldYOffsetToCenterPlayerVisually / (TILE_HEIGHT / 2.0f);
        targetFocusCol += deltaMapUnitsSumForCentering / 2.0f;
        targetFocusRow += deltaMapUnitsSumForCentering / 2.0f;

        return new float[]{targetFocusCol, targetFocusRow};
    }


    public void handleContinuousInput(double deltaTime) {
        if (gameInstance != null && gameInstance.getCurrentGameState() == Game.GameState.IN_GAME) {
            if (player == null || cameraManager == null) return;

            float dCol = 0f;
            float dRow = 0f;
            boolean isPlayerTryingToMove = false;

            if (keysDown.contains(GLFW_KEY_W)) { dRow--; dCol--; isPlayerTryingToMove = true; }
            if (keysDown.contains(GLFW_KEY_S)) { dRow++; dCol++; isPlayerTryingToMove = true; }
            if (keysDown.contains(GLFW_KEY_A)) { dRow++; dCol--; isPlayerTryingToMove = true; }
            if (keysDown.contains(GLFW_KEY_D)) { dRow--; dCol++; isPlayerTryingToMove = true; }

            if (isPlayerTryingToMove) {
                if (cameraManager.isManuallyPanning()) {
                    cameraManager.stopManualPan();
                }

                if (dCol != 0f || dRow != 0f) {
                    float length = (float)Math.sqrt(dCol * dCol + dRow * dRow);
                    if (length != 0) {
                        dCol /= length;
                        dRow /= length;
                    }
                }
                player.setMovementInput(dCol, dRow);
                player.setAction(PlayerModel.Action.WALK);

                if (keysDown.contains(GLFW_KEY_W)) {
                    if (keysDown.contains(GLFW_KEY_D)) player.setDirection(PlayerModel.Direction.EAST);
                    else if (keysDown.contains(GLFW_KEY_A)) player.setDirection(PlayerModel.Direction.NORTH);
                    else player.setDirection(PlayerModel.Direction.NORTH);
                } else if (keysDown.contains(GLFW_KEY_S)) {
                    if (keysDown.contains(GLFW_KEY_D)) player.setDirection(PlayerModel.Direction.SOUTH);
                    else if (keysDown.contains(GLFW_KEY_A)) player.setDirection(PlayerModel.Direction.WEST);
                    else player.setDirection(PlayerModel.Direction.SOUTH);
                } else if (keysDown.contains(GLFW_KEY_D)) {
                    player.setDirection(PlayerModel.Direction.EAST);
                } else if (keysDown.contains(GLFW_KEY_A)) {
                    player.setDirection(PlayerModel.Direction.WEST);
                }
            } else { // No WASD movement keys are being pressed
                player.setMovementInput(0f, 0f);
                // PlayerModel.update() will set Action to IDLE if it was WALK and no input now
            }

            if (!cameraManager.isManuallyPanning()) {
                float[] focusPoint = calculateCameraFocusPoint(player.getMapCol(), player.getMapRow());
                cameraManager.setTargetPosition(focusPoint[0], focusPoint[1]);
            }
        }
    }

    public void performPlayerActionOnCurrentlySelectedTile() {
        if (player == null || map == null || gameInstance == null) return;
        // Allow re-triggering HIT/CHOP to restart animation, or return if action must complete.
        // if (player.getCurrentAction() == PlayerModel.Action.HIT || player.getCurrentAction() == PlayerModel.Action.CHOPPING) {
        //      return; // Or allow restarting. Current PlayerModel.setAction allows restart.
        // }

        int targetR = this.selectedRow;
        int targetC = this.selectedCol;
        if (!map.isValid(targetR, targetC)){ return; }

        // Determine player facing direction towards the target tile
        float dColPlayerToTarget = targetC - player.getMapCol(); // More positive means target is East-ish
        float dRowPlayerToTarget = targetR - player.getMapRow(); // More positive means target is South-ish

        // Crude determination of facing direction based on largest component
        if (Math.abs(dColPlayerToTarget) > Math.abs(dRowPlayerToTarget)) { // Horizontal difference is greater
            if (dColPlayerToTarget > 0) player.setDirection(PlayerModel.Direction.EAST);  // Target is East
            else player.setDirection(PlayerModel.Direction.WEST); // Target is West
        } else if (Math.abs(dRowPlayerToTarget) > Math.abs(dColPlayerToTarget)) { // Vertical difference is greater or equal
            if (dRowPlayerToTarget > 0) player.setDirection(PlayerModel.Direction.SOUTH); // Target is South
            else player.setDirection(PlayerModel.Direction.NORTH); // Target is North
        } else if (dColPlayerToTarget != 0 || dRowPlayerToTarget != 0) { // Equidistant diagonal, prioritize one (e.g. South/East)
            if (dRowPlayerToTarget > 0) player.setDirection(PlayerModel.Direction.SOUTH);
            else if (dColPlayerToTarget > 0) player.setDirection(PlayerModel.Direction.EAST);
                // Add more specific diagonal handling if needed, or default to a primary like South.
            else player.setDirection(PlayerModel.Direction.SOUTH); // Fallback
        }
        // If target is player's current tile, direction doesn't need to change based on target.

        Tile targetTile = map.getTile(targetR, targetC);
        Item selectedItem = null;
        int selectedSlotIndex = gameInstance.getSelectedInventorySlotIndex(); // Game holds the UI selection
        if (selectedSlotIndex >= 0 && selectedSlotIndex < player.getInventorySlots().size()) {
            InventorySlot currentSlot = player.getInventorySlots().get(selectedSlotIndex);
            if (currentSlot != null && !currentSlot.isEmpty()) {
                selectedItem = currentSlot.getItem();
            }
        }

        if (targetTile != null) {
            player.setAction(PlayerModel.Action.HIT); // Default action for interaction
            if (targetTile.getTreeType() != Tile.TreeVisualType.NONE) {
                gameInstance.interactWithTree(targetR, targetC, player, selectedItem);
            } else { // No tree, interact with the ground (digging)
                if (targetTile.getElevation() >= NIVEL_MAR) { // Can only dig land, not water tiles directly
                    Tile.TileType originalType = targetTile.getType();
                    int originalElevation = targetTile.getElevation();

                    // Digging lowers elevation by 1, down to elevation 0.
                    // It won't turn land into water by digging below NIVEL_MAR.
                    int newElevation = Math.max(0, originalElevation - 1);

                    // Ensure digging actually changes something or is above minimum diggable level
                    if (originalElevation > newElevation || (originalElevation == newElevation && originalElevation > 0 && originalElevation >= NIVEL_MAR) ) {
                        map.setTileElevation(targetR, targetC, newElevation); // This will update tile type via determineTileTypeFromElevation

                        Item receivedItem = null;
                        switch (originalType) { // Item received is based on the *original* tile type
                            case GRASS: receivedItem = ItemRegistry.DIRT; break; // Grass yields dirt
                            case SAND:  receivedItem = ItemRegistry.SAND; break;
                            case ROCK:  receivedItem = ItemRegistry.STONE; break;
                            case DIRT:  receivedItem = ItemRegistry.DIRT; break;
                            case SNOW:  receivedItem = ItemRegistry.DIRT; break; // Snow on rock/dirt might yield that
                        }
                        if (receivedItem != null) {
                            player.addItemToInventory(receivedItem, 1);
                        }
                        // map.setTileElevation already marks chunks dirty via LightManager
                        // gameInstance.requestTileRenderUpdate(targetR, targetC); // Redundant if setTileElevation handles it
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
                map.setTileElevation(selectedRow, selectedCol, newElevation); // This method should handle render updates
            }
        }
    }

    public void setSelectedTile(int col, int row) {
        if (map == null) return;
        if (!map.isValid(row, col)) {
            return;
        }
        if (this.selectedRow != row || this.selectedCol != col) {
            int oldSelectedRow = this.selectedRow;
            int oldSelectedCol = this.selectedCol;
            this.selectedRow = row;
            this.selectedCol = col;
            if (gameInstance != null) { // gameInstance might be null during very early init
                if (map.isValid(oldSelectedRow, oldSelectedCol)) {
                    gameInstance.requestTileRenderUpdate(oldSelectedRow, oldSelectedCol);
                }
                gameInstance.requestTileRenderUpdate(this.selectedRow, this.selectedCol);
            }
        }
    }

    public Game getGameInstance() { return this.gameInstance; }
    public int getSelectedRow() { return selectedRow; }
    public int getSelectedCol() { return selectedCol; }
}