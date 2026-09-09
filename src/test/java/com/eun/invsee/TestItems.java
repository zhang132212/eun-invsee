package com.eun.invsee;

import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;

public final class TestItems {
    public static final HolderLookup.Provider REGISTRIES;
    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        REGISTRIES = VanillaRegistries.createLookup();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(REGISTRIES).forEach(DataComponentInitializers.PendingComponents::apply);
    }
    private TestItems() {}
}
