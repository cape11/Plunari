package org.isogame.item;

import org.isogame.constants.Constants;
import org.isogame.game.Game;
import org.isogame.entity.PlayerModel;
import org.isogame.map.Map;
import org.isogame.tile.Tile;

public class ToolItem extends Item {

    public enum ToolType {
        AXE, PICKAXE, SHOVEL, HOE
    }

    private final ToolType toolType;

    public ToolItem(String itemId, String displayName, String description,
                    boolean hasIconTexture, String atlasName, float pixelX, float pixelY, float pixelW, float pixelH,
                    ToolType toolType) {
        super(itemId, displayName, description, ItemType.TOOL, 1, new float[]{0.6f, 0.65f, 0.7f, 1.0f},
                hasIconTexture, atlasName, pixelX, pixelY, pixelW, pixelH);
        this.toolType = toolType;
    }

    @Override
    public boolean onUse(Game game, PlayerModel player, Tile targetTile, int tileR, int tileC) {
        if (targetTile == null) {
            return false;
        }

        switch (this.toolType) {
            case AXE:
                return useAxe(game, player, targetTile, tileR, tileC);
            // Future tools will be handled here
            // case PICKAXE:
            //     return usePickaxe(game, player, targetTile, tileR, tileC);
        }
        return false;
    }

    private boolean useAxe(Game game, PlayerModel player, Tile targetTile, int tileR, int tileC) {
        if (targetTile.getTreeType() != Tile.TreeVisualType.NONE) {
            targetTile.setTreeType(Tile.TreeVisualType.NONE);
            player.addItemToInventory(ItemRegistry.WOOD, 3); // More wood for using the right tool

            Map map = game.getMap();
            if (map != null) {
                map.markChunkAsModified(Math.floorDiv(tileC, Constants.CHUNK_SIZE_TILES), Math.floorDiv(tileR, Constants.CHUNK_SIZE_TILES));
                if (game.getLightManager() != null) {
                    map.queueLightUpdateForArea(tileR, tileC, 1, game.getLightManager());
                }
            }
            game.requestTileRenderUpdate(tileR, tileC);
            return true; // Action was successful
        }
        return false; // Axe has no effect if there's no tree
    }
}