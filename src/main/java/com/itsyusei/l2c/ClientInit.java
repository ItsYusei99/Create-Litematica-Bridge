package com.itsyusei.l2c;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.common.NeoForge;

@OnlyIn(Dist.CLIENT)
public class ClientInit {
    public static void init() {
        NeoForge.EVENT_BUS.register(ClientEvents.class);
    }
}
