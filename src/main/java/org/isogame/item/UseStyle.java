package org.isogame.item;

/**
 * An enumeration to define how an item is used, which dictates the animation played.
 * This is the core of the Terraria-style system.
 */
public enum UseStyle {
    /** The item cannot be used in an animated way (e.g., a resource like wood). */
    NONE,
    /** A standard melee swing animation (for swords, axes, pickaxes). */
    SWING,
    /** Holding the item out in front (for torches, magic staves). */
    HOLD_OUT,
    /** A quick animation for consuming an item (for potions, food). */
    CONSUME,
    /** An aiming animation (for bows, guns). */
    SHOOT
}
