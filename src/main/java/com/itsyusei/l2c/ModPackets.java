package com.itsyusei.l2c;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Common network registration (client and server). References neither
 * Litematica, Create nor client classes: safe on both sides.
 */
public class ModPackets {
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(Litematica2Create.MOD_ID)
                .versioned(Litematica2Create.NETWORK_VERSION);
        registrar.playToServer(
                MaterialsOrderPacket.TYPE,
                MaterialsOrderPacket.STREAM_CODEC,
                MaterialsOrderPacket::handle);
    }
}
