// In a new file: org/isogame/game/PlacementManager.java

package org.isogame.game;

import org.isogame.item.Item;
import org.isogame.map.Map;
import org.isogame.entity.PlayerModel;

import java.util.ArrayList;
import java.util.List;

public class PlacementManager {

    public enum PlacementMode {
        SINGLE,
        LINE // Future mode for walls/lines
        // Could also add FILL, RECTANGLE, etc.
    }

    private final Game game;
    private final Map map;
    private final PlayerModel player;

    private PlacementMode currentMode = PlacementMode.SINGLE;
    private boolean isPlacing = false;

    // Store the start and end coordinates of a placement action (like a drag)
    private int[] startCoords;
    private int[] currentCoords;

    // This list will hold all the tiles that are part of the current preview
    private final List<int[]> placementPreview = new ArrayList<>();

    public PlacementManager(Game game, Map map, PlayerModel player) {
        this.game = game;
        this.map = map;
        this.player = player;
    }

    // Called when the mouse button is first pressed
    // In PlacementManager.java

    public void startPlacement(int c, int r) {
        // This is the first checkpoint
        if (player.getHeldItem() == null || player.getHeldItem().type != Item.ItemType.RESOURCE) {
            // vvv ADD THIS ERROR MESSAGE vvv
            System.err.println("PLACEMENT FAILED (Checkpoint 1): Held item is not a valid RESOURCE block. Item: " + player.getHeldItem());
            return; // The process stops here.
        }

        // Add this success message
        System.out.println("PLACEMENT STARTED (Checkpoint 1 passed) for item: " + player.getHeldItem().getDisplayName());

        this.isPlacing = true;
        this.startCoords = new int[]{c, r};
        this.currentCoords = new int[]{c, r};
        updatePreview();
    }

    // Called every frame the mouse is held down and moved
    public void updatePlacement(int c, int r) {
        if (!isPlacing) return;
        this.currentCoords = new int[]{c, r};
        updatePreview();
    }





    // Called if the placement is cancelled (e.g., right-clicking)
    public void cancelPlacement() {
        reset();
    }

    private void updatePreview() {
        placementPreview.clear();
        if (startCoords == null) return;

        switch (currentMode) {
            case SINGLE:
                // For single mode, the preview is always just the current tile
                placementPreview.add(currentCoords);
                break;
            case LINE:
                // Future logic for line tool would go here.
                // It would calculate all tiles between startCoords and currentCoords.
                // For now, it behaves like SINGLE.
                placementPreview.add(currentCoords);
                break;
        }
    }

    private void reset() {
        this.isPlacing = false;
        this.startCoords = null;
        this.currentCoords = null;
        this.placementPreview.clear();
    }



    public void finalizePlacement() {
        if (!isPlacing) return;

        for (int[] coords : placementPreview) {
            Item heldItem = player.getHeldItem();
            if (heldItem != null && player.getHeldItemCount() > 0) {

                // The 'if' for STRUCTURE MUST come first!
                if (heldItem.getType() == Item.ItemType.STRUCTURE) {
                    game.getStructureManager().addWall(coords[1], coords[0], heldItem.getItemId());
                    player.consumeHeldItem(1);
                }
                // The 'else if' for placing regular blocks comes second.
                else if (map.placeBlock(coords[1], coords[0], heldItem, this.game)) {
                    player.consumeHeldItem(1);
                }

            } else {
                break;
            }
        }
        game.setHotbarDirty(true);
        reset();
    }


    public List<int[]> getPlacementPreview() {
        return placementPreview;
    }

    public boolean isPlacing() {
        return isPlacing;
    }
}