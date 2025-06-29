package org.isogame.savegame;

import java.util.ArrayList;
import java.util.List;

public class GameSaveState {
    public long worldSeed;
    public double pseudoTimeOfDay;
    public PlayerSaveData playerData;
    public MapSaveData mapData;
    public List<EntitySaveData> entityData = new ArrayList<>();

    // NEW FIELD
    public List<TileEntitySaveData> tileEntityData = new ArrayList<>();
}