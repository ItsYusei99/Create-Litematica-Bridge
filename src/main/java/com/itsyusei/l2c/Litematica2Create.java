package com.itsyusei.l2c;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(Litematica2Create.MOD_ID)
public class Litematica2Create {
    public static final String MOD_ID = "litematica2create";
    public static final String NETWORK_VERSION = "2";

    public Litematica2Create(IEventBus modEventBus) {
        modEventBus.addListener(ModPackets::register);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientInit.init();
        }
    }
}
