package org.isogame.gamedata;

import java.util.Map;

/**
 * A data structure that holds all animation information for an entity.
 * This class maps directly to the structure of an entity's animation JSON file.
 */
public class AnimationDefinition {
    public String textureAtlas;
    public int frameWidth;
    public int frameHeight;
    public Map<String, AnimationTrack> animations;

    public static class AnimationTrack {
        public int row;
        public int frames;
        public double frameDuration;
    }
}