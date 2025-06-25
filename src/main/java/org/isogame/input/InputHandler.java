    // src/main/java/org/isogame/input/InputHandler.java
    package org.isogame.input;

    import org.isogame.camera.CameraManager;
    import org.isogame.constants.Constants;
    import org.isogame.entity.Entity;
    import org.isogame.entity.PlayerModel;
    import org.isogame.game.Game;
    import org.isogame.game.GameStateManager;
    import org.isogame.item.Item;
    import org.isogame.item.ItemRegistry;
    import org.isogame.item.UseStyle;
    import org.isogame.map.Map;
    import org.isogame.tile.Tile;

    import static org.lwjgl.glfw.GLFW.*;
    import static org.isogame.constants.Constants.*;

    import java.util.HashSet;
    import java.util.Set;

    public class InputHandler {

        private final long window;
        private final CameraManager cameraManager;
        private Map map;
        private PlayerModel player;
        private final Game gameInstance;

        public int selectedRow = 0;
        public int selectedCol = 0;
        private final Set<Integer> keysDown = new HashSet<>();

        private static final float PLAYER_CAMERA_Y_SCREEN_OFFSET = -75.0f;

        public InputHandler(long window, CameraManager cameraManager, Map map, PlayerModel player, Game gameInstance) {
            this.window = window;
            this.cameraManager = cameraManager;
            this.map = map;
            this.player = player;
            this.gameInstance = gameInstance;
            if (this.player != null) {
                this.selectedRow = player.getTileRow();
                this.selectedCol = player.getTileCol();
            }
        }

        public void updateGameReferences(Map map, PlayerModel player) {
            this.map = map;
            this.player = player;
            if (this.player != null) {
                this.selectedRow = player.getTileRow();
                this.selectedCol = player.getTileCol();
            } else {
                this.selectedRow = 0;
                this.selectedCol = 0;
            }
        }


        public void registerCallbacks(Runnable requestFullMapRegenerationCallback) {
            glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
                if (gameInstance == null) {
                    return;
                }

                if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
                    gameInstance.setCurrentGameState(GameStateManager.State.MAIN_MENU);
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
                        if (!stillPressingMovementKey && player != null) {
                            player.setMovementInput(0f, 0f);
                        }
                    }
                }
            });
        }

        private void handleSingleKeyPressInGame(int key, Runnable requestFullMapRegenerationCallback) {
            if (player == null || map == null) {
                return;
            }
            switch (key) {
                case GLFW_KEY_J:
                    performPlayerActionOnCurrentlySelectedTile();
                    break;
                case GLFW_KEY_F1:
                    System.out.println("DEBUG: Forcing render update for player's current chunk.");
                    gameInstance.requestTileRenderUpdate(player.getTileRow(), player.getTileCol());
                    break;
                case GLFW_KEY_C:
                    if (cameraManager != null) {
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
                    }
                    break;
                case GLFW_KEY_I: gameInstance.toggleInventory(); break;
                case GLFW_KEY_1: gameInstance.setSelectedHotbarSlotIndex(0); break;
                case GLFW_KEY_2: gameInstance.setSelectedHotbarSlotIndex(1); break;
                case GLFW_KEY_3: gameInstance.setSelectedHotbarSlotIndex(2); break;
                case GLFW_KEY_4: gameInstance.setSelectedHotbarSlotIndex(3); break;
                case GLFW_KEY_5: gameInstance.setSelectedHotbarSlotIndex(4); break;
            }
        }

        public float[] calculateCameraFocusPoint(float playerMapCol, float playerMapRow) {
            if (player == null || cameraManager == null || map == null) {
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
            float targetFocusCol = playerMapCol + deltaMapUnitsSumForCentering / 2.0f;
            float targetFocusRow = playerMapRow + deltaMapUnitsSumForCentering / 2.0f;

            return new float[]{targetFocusCol, targetFocusRow};
        }

        public void handleContinuousInput(double deltaTime) {
            if (player == null || cameraManager == null || map == null) {
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
                    if (length != 0) {
                        dCol /= length;
                        dRow /= length;
                    }
                }
                player.setMovementInput(dCol, dRow);

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

        public void performPlayerActionOnCurrentlySelectedTile() {
            if (player == null || map == null || gameInstance == null) {
                return;
            }

            int targetR = this.selectedRow;
            int targetC = this.selectedCol;
            Tile targetTile = map.getTile(targetR, targetC);

            if (targetTile == null) return;

            float distance = Math.abs(targetR - player.getMapRow()) + Math.abs(targetC - player.getMapCol());
            if (distance > Constants.MAX_INTERACTION_DISTANCE) {
                return;
            }

            if (targetTile.hasTileEntity()) {
                // If it does, interact with it. This will open the UI.
                targetTile.getTileEntity().onInteract(gameInstance);
                return; // The interaction is handled, so we don't need to do anything else.
            }

            float dColPlayerToTarget = targetC - player.getMapCol();
            float dRowPlayerToTarget = targetR - player.getMapRow();
            if (Math.abs(dColPlayerToTarget) > Math.abs(dRowPlayerToTarget)) {
                player.setDirection(dColPlayerToTarget > 0 ? PlayerModel.Direction.EAST : PlayerModel.Direction.WEST);
            } else {
                player.setDirection(dRowPlayerToTarget > 0 ? PlayerModel.Direction.SOUTH : PlayerModel.Direction.NORTH);
            }

            Item heldItem = player.getHeldItem();

            if (heldItem != null) {
                heldItem.onUse(gameInstance, player, targetTile, targetR, targetC);
            } else {
                player.setAction(Entity.Action.SWING);

                if (targetTile.getLooseRockType() != Tile.LooseRockType.NONE) {
                    player.addItemToInventory(ItemRegistry.getItem("loose_rock"), 1);
                    targetTile.setLooseRockType(Tile.LooseRockType.NONE);
                    gameInstance.requestTileRenderUpdate(targetR, targetC);
                } else if (targetTile.getTreeType() != Tile.TreeVisualType.NONE) {
                    player.addItemToInventory(ItemRegistry.getItem("stick"), 1);
                    targetTile.takeDamage(1);
                }
            }
        }


        private void modifySelectedTileElevation(int amount) {
            if (map == null || gameInstance == null) {
                return;
            }
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
            if (map == null) {
                return;
            }
            if (this.selectedRow == row && this.selectedCol == col) {
                return;
            }

            int oldSelectedRow = this.selectedRow;
            int oldSelectedCol = this.selectedCol;

            this.selectedRow = row;
            this.selectedCol = col;

            if (gameInstance != null) {
                gameInstance.requestTileRenderUpdate(oldSelectedRow, oldSelectedCol);
                gameInstance.requestTileRenderUpdate(this.selectedRow, this.selectedCol);

                gameInstance.requestTileRenderUpdate(oldSelectedRow + 1, oldSelectedCol);
                gameInstance.requestTileRenderUpdate(oldSelectedRow - 1, oldSelectedCol);
                gameInstance.requestTileRenderUpdate(oldSelectedRow, oldSelectedCol + 1);
                gameInstance.requestTileRenderUpdate(oldSelectedRow, oldSelectedCol - 1);
            }
        }

        public Game getGameInstance() { return this.gameInstance; }
        public int getSelectedRow() { return selectedRow; }
        public int getSelectedCol() { return selectedCol; }
    }