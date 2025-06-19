package org.isogame.entity;

import org.isogame.game.Game;
import org.isogame.map.AStarPathfinder;
import org.isogame.map.Map;
import org.isogame.map.PathNode;
import org.isogame.savegame.EntitySaveData;
import org.isogame.tile.Tile;

import java.util.List;
import java.util.Random;

public class Cow extends Animal {

    // ---!!! THIS IS THE SECTION TO EDIT !!!---

    // Step 1: The starting pixel of your entire cow animation block.
    // You provided these values.
    public static final int SPRITESHEET_START_X_PIXEL = 385;
    public static final int SPRITESHEET_START_Y_PIXEL = 1668;

    // Step 2: The size of a single frame for the cow.
    public static final int COW_FRAME_WIDTH = 64;
    public static final int COW_FRAME_HEIGHT = 64;

    // The code will automatically calculate the starting frame column and row.
    private static final int STARTING_FRAME_COL = SPRITESHEET_START_X_PIXEL / COW_FRAME_WIDTH; // Result: 5
    private static final int STARTING_FRAME_ROW = SPRITESHEET_START_Y_PIXEL / COW_FRAME_HEIGHT; // Result: 26

    // Step 3: The local row for each animation *within the cow block*.
    // For example, if "Walk South" is the top row of your cow sprites, its offset is 0.
    public static final int ROW_OFFSET_WALK_SOUTH = 0;
    public static final int ROW_OFFSET_WALK_WEST  = 3;
    public static final int ROW_OFFSET_WALK_NORTH = 2;
    public static final int ROW_OFFSET_WALK_EAST  = 1;

    // Step 4: The number of frames in one walk cycle.
    public static final int FRAMES_PER_CYCLE = 3;

    public Cow(float startRow, float startCol) {
        super(startRow, startCol);
        this.frameDuration = 0.25;
    }

    @Override
    public void populateSaveData(EntitySaveData data) {
        super.populateSaveData(data); // Call parent to save common data
        data.entityType = "COW"; // Identify this as a Cow
    }

    @Override
    public int getFrameWidth() {
        return COW_FRAME_WIDTH;
    }

    @Override
    public int getFrameHeight() {
        return COW_FRAME_HEIGHT;
    }

    /**
     * This method now returns the ABSOLUTE row on the spritesheet by adding
     * the starting row offset to the direction-specific offset.
     */
    @Override
    public int getAnimationRow() {
        int relativeRow = ROW_OFFSET_WALK_SOUTH; // Default direction
        switch (currentDirection) {
            case NORTH: relativeRow = ROW_OFFSET_WALK_NORTH; break;
            case WEST:  relativeRow = ROW_OFFSET_WALK_WEST;  break;
            case SOUTH: relativeRow = ROW_OFFSET_WALK_SOUTH; break;
            case EAST:  relativeRow = ROW_OFFSET_WALK_EAST;  break;
        }
        return STARTING_FRAME_ROW + relativeRow;
    }

    /**
     * This method now returns the ABSOLUTE column on the spritesheet by adding
     * the starting column offset to the current animation frame.
     */
    @Override
    public int getVisualFrameIndex() {
        return STARTING_FRAME_COL + (currentFrameIndex % FRAMES_PER_CYCLE);
    }

    @Override
    public String getDisplayName() {
        return "Cow";
    }
}
