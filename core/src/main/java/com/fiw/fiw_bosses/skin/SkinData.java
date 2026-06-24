package com.fiw.fiw_bosses.skin;

/** Carries a downloaded skin PNG together with its model type (slim/classic). */
public class SkinData {
    public final byte[] png;
    /** True = Alex / slim arms (3 px wide). False = Steve / classic arms (4 px wide). */
    public final boolean slim;

    public SkinData(byte[] png, boolean slim) {
        this.png  = png;
        this.slim = slim;
    }
}
