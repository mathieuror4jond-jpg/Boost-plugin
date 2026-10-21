package com.boostplugin;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

public enum Boost {
    VEIN("vein", "Veine d'or", NamedTextColor.GOLD, "Minerais aléatoires en minant (toutes les 10 s)"),
    ZEPHYR("zephyr", "Zéphyr", NamedTextColor.AQUA, "Vitesse, saut et aucun dégât de chute"),
    TITAN("titan", "Poing du Titan", NamedTextColor.DARK_RED, "Force accrue"),
    SKIN("skin", "Peau de pierre", NamedTextColor.GRAY, "Résistance et immunité au feu"),
    ABYSS("abyss", "Abysses", NamedTextColor.BLUE, "Respiration, nage rapide, vision sous l'eau"),
    GREEN("green", "Main verte", NamedTextColor.GREEN, "Minage rapide et saturation"),
    PHOENIX("phoenix", "Phénix", NamedTextColor.RED, "Régénération et résurrection toutes les 10 min");

    public final String id;
    public final String displayName;
    public final TextColor color;
    public final String desc;

    Boost(String id, String displayName, TextColor color, String desc) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
        this.desc = desc;
    }

    public static Boost byId(String id) {
        if (id == null) return null;
        for (Boost b : values()) {
            if (b.id.equalsIgnoreCase(id)) return b;
        }
        return null;
    }
}
