package org.isogame.savegame;

import java.util.List;

public class MapSaveData {
    public int width;
    public int height;
    public List<List<TileSaveData>> tiles; // A 2D list of TileSaveData
}