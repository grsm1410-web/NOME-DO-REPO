package com.craterborn.registry;

import com.craterborn.Craterborn;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/** Tags do mod. */
public final class ModTags {
    /** Comidas que um demônio ainda consegue comer (os efeitos valem; a fome não, pois ela não existe mais). */
    public static final TagKey<Item> DEMON_EDIBLE = TagKey.create(Registries.ITEM, Craterborn.id("demon_edible"));

    private ModTags() {
    }
}
