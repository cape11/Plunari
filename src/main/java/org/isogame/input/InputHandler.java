package org.isogame.input;

import org.isogame.camera.CameraManager;
import org.isogame.constants.Constants;
import org.isogame.entity.PlayerModel;
import org.isogame.game.Game;
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
    private Map map; // Made non-final to be updated
    private PlayerModel player; // Made non-final to be updated
    private final Game gameInstance;

    public int selectedRow = 0;
    public int selectedCol = 0;
    private final Set<Integer> keysDown = new HashSet<>();

    private static final float PLAYER_CAMERA_Y_SCREEN_OFFSET = -75.0f;

    public InputHandler(long window, CameraManager cameraManager, Map map, PlayerModel player, Game gameInstance) {
        this.window = window;
        this.cameraManager = cameraManager;
        this.map = map; // Can be null initially (for main menu)
        this.player = player; // Can be null initially
        this.gameInstance = gameInstance;
        if (this.player != null && this.map != null) { // Only set selected if player/map are valid
            this.selectedRow = player.getTileRow();
            this.selectedCol = player.getTileCol();
        }
        System.out.println("InputHandler: Initialized. GameInstance: " + (gameInstance != null) + ", Map: " + (map != null) + ", Player: " + (player != null));
    }

    /**
     * Updates the internal map and player references.
     * Called when the game starts or loads a new world.
     * @param map The current game map.
     * @param player The current player model.
     */
    public void updateGameReferences(Map map, PlayerModel player) {
        this.map = map;
        this.player = player;
        if (this.player != null && this.map != null) {
            this.selectedRow = player.getTileRow();
            this.selectedCol = player.getTileCol();
        } else {
            this.selectedRow = 0;
            this.selectedCol = 0;
        }
        System.out.println("InputHandler: Updated game references. Map: " + (this.map != null) + ", Player: " + (this.player != null));
    }


    public void registerCallbacks(Runnable requestFullMapRegenerationCallback) {
        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            if (gameInstance == null) { // Should not happen if gameInstance is final and set in constructor
                System.err.println("InputHandler (KeyCallback): gameInstance is null!");
                return;
            }

            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
                if (gameInstance.getCurrentGameState() == Game.GameState.IN_GAME) {
                    gameInstance.setCurrentGameState(Game.GameState.MAIN_MENU);
                } else if (gameInstance.getCurrentGameState() == Game.GameState.MAIN_MENU) {
                    glfwSetWindowShouldClose(window, true);
                } else {
                    glfwSetWindowShouldClose(window, true);
                }
                return;
            }

            if (gameInstance.getCurrentGameState() == Game.GameState.IN_GAME) {
                if (player == null || map == null) { // Guard for in-game actions
                    // System.err.println("InputHandler (KeyCallback): In-game key event but player or map is null.");
                    return;
                }
                if (action == GLFW_PRESS) {
                    keysDown.add(key);
                    handleSingleKeyPressInGame(key, requestFullMapRegenerationCallback);
                } else if (action == GLFW_RELEASE) {
                    keysDown.remove(key);
                    if ((key == GLFW_KEY_W || key == GLFW_KEY_S || key == GLFW_KEY_A || key == GLFW_KEY_D)) {
                        boolean stillPressingMovementKey = keysDown.stream().anyMatch(k ->
                                k == GLFW_KEY_W || k == GLFW_KEY_S || k == GLFW_KEY_A || k == GLFW_KEY_D);
                        if (!stillPressingMovementKey) {
                            player.setMovementInput(0f, 0f);
                        }
                    }
                }
            } else { // Not IN_GAME (e.g., MAIN_MENU)
                if (action == GLFW_PRESS) {
                    keysDown.add(key);
                } else if (action == GLFW_RELEASE) {
                    keysDown.remove(key);
                }
            }
        });
    }

    private void handleSingleKeyPressInGame(int key, Runnable requestFullMapRegenerationCallback) {
        // This method assumes player and map are not null, guarded by the caller.
        switch (key) {
            case GLFW_KEY_J:
                performPlayerActionOnCurrentlySelectedTile();
                break;
            case GLFW_KEY_C:
                if (cameraManager != null) { // player and map are already checked by caller
                    cameraManager.stopManualPan();
                    float[] focusPoint = calculateCameraFocusPoint(player.getMapCol(), player.getMapRow());
                    cameraManager.setTargetPositionInstantly(focusPoint[0], focusPoint[1]);
                }
                break;
            case GLFW_KEY_G: if (requestFullMapRegenerationCallback != null) requestFullMapRegenerationCallback.run(); break;
            case GLFW_KEY_F: player.toggleLevitate(); break;
            case GLFW_KEY_Q: modifySelectedTileElevation(-1); break;
            case GLFW_KEY_E: modifySelectedTileElevation(1); break;
            case GLFW_KEY_L: map.toggleTorch(selectedRow, selectedCol); break;
            case GLFW_KEY_H: gameInstance.toggleHotbar(); break;
            case GLFW_KEY_F5: gameInstance.toggleShowDebugOverlay(); break;
            case GLFW_KEY_F6: gameInstance.decreaseRenderDistance(); break;
            case GLFW_KEY_F7: gameInstance.increaseRenderDistance(); break;
            case GLFW_KEY_F9:
                String currentWorld = gameInstance.getCurrentWorldName();
                if (currentWorld != null && !currentWorld.trim().isEmpty()) {
                    gameInstance.saveGame(currentWorld);
                } else {
                    System.out.println("InputHandler: Cannot save, no current world name set in Game.");
                }
                break;
            case GLFW_KEY_I: gameInstance.toggleInventory(); break;
            case GLFW_KEY_1: gameInstance.setSelectedHotbarSlotIndex(0); break;
            case GLFW_KEY_2: gameInstance.setSelectedHotbarSlotIndex(1); break;
            case GLFW_KEY_3: gameInstance.setSelectedHotbarSlotIndex(2); break;
            case GLFW_KEY_4: gameInstance.setSelectedHotbarSlotIndex(3); break;
            case GLFW_KEY_5: gameInstance.setSelectedHotbarSlotIndex(4); break;
            case GLFW_KEY_6: if(Constants.HOTBAR_SIZE > 5) gameInstance.setSelectedHotbarSlotIndex(5); break;
            case GLFW_KEY_7: if(Constants.HOTBAR_SIZE > 6) gameInstance.setSelectedHotbarSlotIndex(6); break;
            case GLFW_KEY_8: if(Constants.HOTBAR_SIZE > 7) gameInstance.setSelectedHotbarSlotIndex(7); break;
            case GLFW_KEY_9: if(Constants.HOTBAR_SIZE > 8) gameInstance.setSelectedHotbarSlotIndex(8); break;
            case GLFW_KEY_0: if(Constants.HOTBAR_SIZE > 9) gameInstance.setSelectedHotbarSlotIndex(9); break;
        }
    }

    public float[] calculateCameraFocusPoint(float playerMapCol, float playerMapRow) {
        // This method assumes player, cameraManager, and map are not null when called for in-game.
        float targetFocusCol = playerMapCol;
        float targetFocusRow = playerMapRow;

        if (player == null || cameraManager == null || map == null) { // Should be guarded by caller if in-game
            return new float[]{playerMapCol, playerMapRow};
        }

        int playerElevation = 0;
        Tile playerTile = map.getTile(player.getTileRow(), player.getTileCol());
        if (playerTile != null) {
            playerElevation = playerTile.getElevation();
        }

        float playerVisualCenterWorldYOffset = (-playerElevation * TILE_THICKNESS) - (PLAYER_WORLD_RENDER_HEIGHT / 2.0f);
        float totalWorldYOffsetToCenterPlayerVisually = playerVisualCenterWorldYOffset - (PLAYER_CAMERA_Y_SCREEN_OFFSET / cameraManager.getZoom());
        float deltaMapUnitsSumForCentering = totalWorldYOffsetToCenterPlayerVisually / (TILE_HEIGHT / 2.0f);
        targetFocusCol += deltaMapUnitsSumForCentering / 2.0f;
        targetFocusRow += deltaMapUnitsSumForCentering / 2.0f;

        return new float[]{targetFocusCol, targetFocusRow};
    }


    public void handleContinuousInput(double deltaTime) {
        if (gameInstance != null && gameInstance.getCurrentGameState() == Game.GameState.IN_GAME) {
            if (player == null || cameraManager == null || map == null) { // Guard for in-game actions
                // System.err.println("InputHandler (ContinuousInput): player, cameraManager or map is null.");
                return;
            }

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
                    if (length != 0) { // Avoid division by zero
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
            } else {
                player.setMovementInput(0f, 0f);
            }

            if (!cameraManager.isManuallyPanning()) {
                float[] focusPoint = calculateCameraFocusPoint(player.getMapCol(), player.getMapRow());
                cameraManager.setTargetPosition(focusPoint[0], focusPoint[1]);
            }
        }
    }


    public void performPlayerActionOnCurrentlySelectedTile() {
        if (player == null || map == null || gameInstance == null) {
            return;
        }

        int targetR = this.selectedRow;
        int targetC = this.selectedCol;
        Tile targetTile = map.getTile(targetR, targetC);

        if (targetTile == null) return;

        // Check interaction range
        float distance = Math.abs(targetR - player.getMapRow()) + Math.abs(targetC - player.getMapCol());
        if (distance > Constants.MAX_INTERACTION_DISTANCE) {
            return;
        }

        // 1. Get the item the player is holding.
        Item heldItem = player.getHeldItem();

        // 2. Set player animation and direction to face the target.
        player.setAction(PlayerModel.Action.HIT);
        float dColPlayerToTarget = targetC - player.getMapCol();
        float dRowPlayerToTarget = targetR - player.getMapRow();
        if (Math.abs(dColPlayerToTarget) > Math.abs(dRowPlayerToTarget)) {
            player.setDirection(dColPlayerToTarget > 0 ? PlayerModel.Direction.EAST : PlayerModel.Direction.WEST);
        } else {
            player.setDirection(dRowPlayerToTarget > 0 ? PlayerModel.Direction.SOUTH : PlayerModel.Direction.NORTH);
        }

        // 3. Attempt to use the item. Its onUse method will contain the specific logic.
        boolean actionConsumed = false;
        if (heldItem != null) {
            // The onUse method now correctly gets the game instance and tile coordinates
            actionConsumed = heldItem.onUse(gameInstance, player, targetTile, targetR, targetC);
        }

        // 4. If the item's action was not consumed, perform the default "punch" action.
        if (!actionConsumed) {
            if (targetTile.getLooseRockType() != Tile.LooseRockType.NONE) {
                player.addItemToInventory(ItemRegistry.LOOSE_ROCK, 1);
                targetTile.setLooseRockType(Tile.LooseRockType.NONE);
                map.markChunkAsModified(Math.floorDiv(targetC, Constants.CHUNK_SIZE_TILES), Math.floorDiv(targetR, Constants.CHUNK_SIZE_TILES));
                gameInstance.requestTileRenderUpdate(targetR, targetC); // <-- This is the issue

                gameInstance.requestTileRenderUpdate(targetR, targetC);
            } else if (targetTile.getTreeType() != Tile.TreeVisualType.NONE) {
                // Punching a tree gives a stick
                player.addItemToInventory(ItemRegistry.STICK, 1);
            } else if (targetTile.getElevation() >= Constants.NIVEL_MAR) {
                // Default digging action for non-rock tiles
                if (targetTile.getType() != Tile.TileType.ROCK) {
                    Tile.TileType originalType = targetTile.getType();
                    map.setTileElevation(targetR, targetC, targetTile.getElevation() - 1);
                    // Give appropriate item for punching the ground
                    if (originalType == Tile.TileType.SAND) {
                        player.addItemToInventory(ItemRegistry.SAND, 1);
                    } else {
                        player.addItemToInventory(ItemRegistry.DIRT, 1);
                    }
                }
            }
        }
    }

    private void modifySelectedTileElevation(int amount) {
        if (map == null || gameInstance == null) {
            System.err.println("InputHandler: Cannot modify elevation, map or gameInstance is null.");
            return;
        }
        Tile tile = map.getTile(selectedRow, selectedCol);
        if (tile != null) {
            int currentElevation = tile.getElevation();
            int newElevation = Math.max(0, Math.min(ALTURA_MAXIMA, currentElevation + amount));
            if (newElevation != currentElevation) {
                map.setTileElevation(selectedRow, selectedCol, newElevation);
            }
        } else {
            System.err.println("InputHandler: Cannot modify elevation, tile at ("+selectedRow+","+selectedCol+") is null or could not be generated.");
        }
    }

    public void setSelectedTile(int col, int row) {
        if (map == null) { // Map might be null if called during menu state before game world init
            // System.out.println("InputHandler: setSelectedTile called but map is null (likely menu state).");
            return;
        }

        Tile newSelectedTile = map.getTile(row, col);
        if (newSelectedTile == null) {
            // System.out.println("InputHandler: Cannot select tile at (" + row + "," + col + "), not a valid map location or failed to generate.");
            return;
        }

        if (this.selectedRow != row || this.selectedCol != col) {
            int oldSelectedRow = this.selectedRow;
            int oldSelectedCol = this.selectedCol;
            this.selectedRow = row;
            this.selectedCol = col;

            if (gameInstance != null) {
                Tile oldTile = map.getTile(oldSelectedRow, oldSelectedCol);
                if (oldTile != null) { // Only request update if old tile was valid
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