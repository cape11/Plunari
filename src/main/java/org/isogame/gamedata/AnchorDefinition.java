package org.isogame.gamedata;

import java.util.Map;

/**
 * A data structure that holds all animation anchor points for an entity.
 * This class maps directly to the structure of an entity's anchors JSON file.
 */
public class AnchorDefinition {

    public Map<String, AnchorPoint> anchors;

    public static class AnchorPoint {
        public float dx;
        public float dy;
        public float rotation;
        public boolean drawBehind;
    }
}