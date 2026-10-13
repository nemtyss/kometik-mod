package com.kozmetik;

public enum Category {
    SWORD("kilic"),
    TOTEM("totem"),
    SKIN("skin");

    /** .minecraft/kozmetik/<folder> klasörü */
    public final String folder;

    Category(String folder) { this.folder = folder; }

    /** builtin.json ve doku klasörü anahtarı */
    public String resourceKey() { return name().toLowerCase(); }
}
