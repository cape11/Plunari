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

        this.useStyle = UseStyle.SWING;
        this.useTime = 25;
        this.useAnimation = 25;
        this.damage = 4;
    }

    /**
     * --- THIS IS THE FIX ---
     * Adds a public getter for the tool type.
     */
    public ToolType getToolType() {
        return this.toolType;
    }
}
