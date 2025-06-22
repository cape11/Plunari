package org.isogame.entity;

import com.google.gson.Gson;
import org.isogame.gamedata.AnimationDefinition;
import org.isogame.savegame.EntitySaveData;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

public class Cow extends Animal {

    // --- NEW: These constants will hold the *absolute* starting position from the JSON ---
    private int STARTING_FRAME_COL = 0;
    private int STARTING_FRAME_ROW = 0;

    public Cow(float startRow, float startCol) {
        super(startRow, startCol);
        loadAnimationDefinition("/data/animations/cow_animations.json");
        if (this.animDef != null) {
            this.frameDuration = animDef.animations.values().stream()
                    .findFirst()
                    .map(track -> track.frameDuration)
                    .orElse(0.25);
            // --- NEW: Calculate the starting positions after loading ---
            this.STARTING_FRAME_COL = 385 / getFrameWidth();  // 385 is SPRITESHEET_START_X_PIXEL
            this.STARTING_FRAME_ROW = 1668 / getFrameHeight(); // 1668 is SPRITESHEET_START_Y_PIXEL
        }
    }

    @Override
    public void populateSaveData(EntitySaveData data) {
        super.populateSaveData(data);
        data.entityType = "COW";
    }

    @Override
    public int getFrameWidth() {
        return (animDef != null) ? animDef.frameWidth : 64;
    }

    @Override
    public int getFrameHeight() {
        return (animDef != null) ? animDef.frameHeight : 64;
    }

    @Override
    public int getAnimationRow() {
        if (animDef == null || animDef.animations == null) return 0;

        String animKey;
        switch (currentDirection) {
            case NORTH: animKey = "walk_north"; break;
            case WEST:  animKey = "walk_west";  break;
            case SOUTH: animKey = "walk_south"; break;
            case EAST:  animKey = "walk_east";  break;
            default:    animKey = "walk_south"; break;
        }

        AnimationDefinition.AnimationTrack track = animDef.animations.get(animKey);
        // The "row" in the JSON is now treated as a local offset
        return STARTING_FRAME_ROW + ((track != null) ? track.row : 0);
    }

    @Override
    public int getVisualFrameIndex() {
        if (animDef == null || animDef.animations == null) return 0;

        String animKey;
        switch (currentDirection) {
            case NORTH: animKey = "walk_north"; break;
            case WEST:  animKey = "walk_west";  break;
            case SOUTH: animKey = "walk_south"; break;
            case EAST:  animKey = "walk_east";  break;
            default:    animKey = "walk_south"; break;
        }

        AnimationDefinition.AnimationTrack track = animDef.animations.get(animKey);
        int maxFrames = (track != null) ? track.frames : 1;

        return STARTING_FRAME_COL + (currentFrameIndex % maxFrames);
    }

    @Override
    public String getDisplayName() {
        return "Cow";
    }
}