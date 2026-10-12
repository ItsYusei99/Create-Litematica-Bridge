package com.itsyusei.l2c;

import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.verifier.SchematicVerifier;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.malilib.interfaces.ICompletionListener;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Client side: runs Litematica's verifier on a placement and orders a clipboard
 * with only the missing / wrong blocks (extra blocks are skipped).
 */
@OnlyIn(Dist.CLIENT)
public final class VerifyOrder {
    private static final Set<String> PENDING = new HashSet<>();

    private VerifyOrder() {
    }

    public static void startVerifyFlow(SchematicPlacement placement) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (!PENDING.add(placement.getName())) {
            tell(Component.literal("§e[L2C] Verification already running..."));
            return;
        }
        try {
            SchematicVerifier verifier = placement.getSchematicVerifier();
            verifier.reset();
            verifier.startVerification(mc.level, SchematicWorldHandler.getSchematicWorld(), placement,
                    new ICompletionListener() {
                        @Override
                        public void onTaskCompleted() {
                            PENDING.remove(placement.getName());
                            Minecraft.getInstance().execute(() -> sendMismatchOrder(placement, verifier));
                        }

                        @Override
                        public void onTaskAborted() {
                            PENDING.remove(placement.getName());
                            Minecraft.getInstance().execute(() ->
                                    tell(Component.literal("§c[L2C] Verification cancelled")));
                        }
                    });
            tell(Component.literal("§e[L2C] Verifying '" + placement.getName() + "'..."));
        } catch (Exception e) {
            PENDING.remove(placement.getName());
            tell(Component.literal("§c[L2C] Could not start verification: " + e.getMessage()));
        }
    }

    private static void sendMismatchOrder(SchematicPlacement placement, SchematicVerifier verifier) {
        Set<String> ignored = IgnoreList.load();
        Map<Item, Integer> totals = new HashMap<>();
        for (SchematicVerifier.BlockMismatch mismatch : verifier.getMismatchOverviewCombined()) {
            if (mismatch.mismatchType != SchematicVerifier.MismatchType.MISSING
                    && mismatch.mismatchType != SchematicVerifier.MismatchType.WRONG_BLOCK
                    && mismatch.mismatchType != SchematicVerifier.MismatchType.WRONG_STATE) {
                continue;
            }
            BlockState expected = mismatch.stateExpected;
            if (expected == null || expected.isAir()) {
                continue;
            }
            Item item = expected.getBlock().asItem();
            if (item == null) {
                continue;
            }
            ItemStack probe = new ItemStack(item);
            if (probe.isEmpty() || IgnoreList.isIgnored(probe, ignored)) {
                continue;
            }
            totals.merge(item, mismatch.count, Integer::sum);
        }
        if (totals.isEmpty()) {
            tell(Component.literal("§a[L2C] '" + placement.getName() + "' matches the world. Nothing to order."));
            return;
        }
        List<MaterialsOrderPacket.Entry> payload = new ArrayList<>(totals.size());
        for (Map.Entry<Item, Integer> e : totals.entrySet()) {
            payload.add(new MaterialsOrderPacket.Entry(new ItemStack(e.getKey(), 1), e.getValue()));
        }
        ClientEvents.sendEntries(placement.getName(), payload, "mismatches");
    }

    private static void tell(Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(message, false);
        }
    }

    @SuppressWarnings("unused")
    private static String describe(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }
}
