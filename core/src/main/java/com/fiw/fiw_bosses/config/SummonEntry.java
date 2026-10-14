package com.fiw.fiw_bosses.config;

/**
 * One way for players to start a boss without an admin: right-click with an item,
 * optionally only on a specific block (an altar).
 */
public class SummonEntry {
    // Vanilla item id ("minecraft:nether_star"). Ignored when toolId is set.
    public String item;
    // Optional Fiw Tools item id. Takes precedence over item.
    public String toolId;
    // Optional exact item name the stack must have (color codes ignored), so a renamed
    // vanilla item can be a summon key without every copy of that item summoning.
    public String name;
    // Optional block id. When set the item only works when used on that block and the
    // boss appears on top of it. When absent the boss appears on the clicked block, or
    // a few blocks in front of the player when used in the air.
    public String altar;
    // Remove one item from the stack on a successful summon.
    public boolean consume = true;
    // Per-player wait between summons of this boss with this entry. 0 = none.
    public int cooldownSeconds = 0;
    // Optional difficulty variant name from the boss's "variants".
    public String variant;
    // Optional server-wide announcement. Placeholders: {player}, {boss}.
    public String message;

    /** True when this entry names an item to match. */
    public boolean hasItem() {
        return (toolId != null && !toolId.isEmpty()) || (item != null && !item.isEmpty());
    }
}
