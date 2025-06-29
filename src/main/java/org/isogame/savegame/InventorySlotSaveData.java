package org.isogame.savegame;

import java.util.HashMap;
import java.util.Map;

public class InventorySlotSaveData {
    public String itemId;
    public int quantity;

    // Helper method to convert this object to a Map for storage
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("itemId", itemId);
        map.put("quantity", quantity);
        return map;
    }

    // Helper method to create an instance from a Map
    public static InventorySlotSaveData fromMap(Map<String, Object> map) {
        if (map == null) return null;
        InventorySlotSaveData data = new InventorySlotSaveData();
        data.itemId = (String) map.get("itemId");
        // Use Number to avoid casting issues with different numeric types (e.g., Double vs Integer)
        Number qty = (Number) map.get("quantity");
        data.quantity = (qty != null) ? qty.intValue() : 0;
        return data;
    }
}