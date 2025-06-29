package org.isogame.tile;

import org.isogame.game.Game;
import org.isogame.item.InventorySlot;
import java.util.List;
import org.isogame.savegame.TileEntitySaveData;

public abstract class TileEntity {
    protected final int row;
    protected final int col;

    public TileEntity(int row, int col) {
        this.row = row;
        this.col = col;
    }

    // All tile entities will have their own update logic
    public abstract void update(double deltaTime, Game game);

    // Methods for interacting with the player
    public abstract void onInteract(Game game);
    public abstract TileEntitySaveData getSaveData();



    // Getters
    public int getRow() { return row; }
    public int getCol() { return col; }
}