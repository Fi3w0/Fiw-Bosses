package com.fiw.fiw_bosses.config;

public class SkinDefinition {
    public String type  = "player"; // "player" or "file"
    public String value = "Steve";  // player name/UUID or filename
    /**
     * For "file" type skins: set true if the skin uses the Alex/slim arm model (3-px arms).
     * For "player" type skins: auto-detected from the Mojang API — this field is ignored.
     */
    public boolean slim = false;
}
