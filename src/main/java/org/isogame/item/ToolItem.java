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

        // Call the parent constructor
        super(itemId, displayName, description, ItemType.TOOL, 1, new float[]{0.6f, 0.65f, 0.7f, 1.0f},
                hasIconTexture, atlasName, pixelX, pixelY, pixelW, pixelH);

        this.toolType = toolType;

        // --- CORE CHANGE: Set the data properties for this tool ---
        this.useStyle = UseStyle.SWING; // This is a swinging tool.
        this.useTime = 25;              // A slightly slower swing speed for a tool.
        this.useAnimation = 25;
        this.damage = 4;                // Has a base damage value.
    }

    // The onUse method is now inherited from Item.java and works for all tools.
    // We no longer need a custom onUse or a useAxe() method here!
}
