package com.itsyusei.l2c;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Client -> server materials order. Common to both sides:
 * only uses vanilla + NeoForge.
 */
public record MaterialsOrderPacket(String placementName, List<Entry> entries) implements CustomPacketPayload {
    public record Entry(ItemStack icon, int amount) {
    }

    public static final CustomPacketPayload.Type<MaterialsOrderPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    Litematica2Create.MOD_ID, "materials_order"));

    private static final int MAX_ENTRIES = 4096;
    private static final int MAX_AMOUNT = 1_000_000;

    public static final StreamCodec<RegistryFriendlyByteBuf, MaterialsOrderPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public void encode(RegistryFriendlyByteBuf buf, MaterialsOrderPacket packet) {
                    buf.writeUtf(packet.placementName(), 128);
                    buf.writeVarInt(packet.entries().size());
                    for (Entry entry : packet.entries()) {
                        ItemStack.STREAM_CODEC.encode(buf, entry.icon());
                        buf.writeVarInt(entry.amount());
                    }
                }

                @Override
                public MaterialsOrderPacket decode(RegistryFriendlyByteBuf buf) {
                    String name = buf.readUtf(128);
                    int count = buf.readVarInt();
                    List<Entry> entries = new ArrayList<>(Math.min(count, MAX_ENTRIES));
                    for (int i = 0; i < count && entries.size() < MAX_ENTRIES; i++) {
                        ItemStack icon = ItemStack.STREAM_CODEC.decode(buf);
                        int amount = buf.readVarInt();
                        if (!icon.isEmpty() && amount >= 1 && amount <= MAX_AMOUNT) {
                            icon.setCount(1);
                            entries.add(new Entry(icon, amount));
                        }
                    }
                    return new MaterialsOrderPacket(name, entries);
                }
            };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MaterialsOrderPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ServerClipboardGiver.giveClipboard(
                context.player(), packet.placementName(), packet.entries()));
    }
}
