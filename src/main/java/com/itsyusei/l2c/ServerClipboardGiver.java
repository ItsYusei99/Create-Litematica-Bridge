package com.itsyusei.l2c;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.equipment.clipboard.ClipboardContent;
import com.simibubi.create.content.equipment.clipboard.ClipboardEntry;
import com.simibubi.create.content.equipment.clipboard.ClipboardOverrides;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Server side: fabricates a Create clipboard identical to the one
 * printed by the schematicannon and hands it to the player.
 * Only uses vanilla + NeoForge + Create: safe on dedicated servers.
 */
public class ServerClipboardGiver {
    /** Entries per page, same as the schematicannon checklist. */
    private static final int ENTRIES_PER_PAGE = 7;

    public static void giveClipboard(Player player,
                                     String placementName,
                                     List<MaterialsOrderPacket.Entry> entries) {
        if (player == null || entries == null || entries.isEmpty()) {
            return;
        }

        Item clipboardItem = BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("create", "clipboard"));
        if (clipboardItem == null) {
            player.displayClientMessage(
                    Component.literal("§c[L2C] Create clipboard not found"), false);
            return;
        }

        List<ClipboardEntry> flat = new ArrayList<>(entries.size());
        int total = 0;
        for (MaterialsOrderPacket.Entry entry : entries) {
            ItemStack icon = entry.icon().copy();
            icon.setCount(1);
            MutableComponent text = Component.literal(
                    entry.amount() + "x " + icon.getHoverName().getString());
            flat.add(new ClipboardEntry(false, text).displayItem(icon, entry.amount()));
            total += entry.amount();
        }

        List<List<ClipboardEntry>> pages = new ArrayList<>();
        for (int i = 0; i < flat.size(); i += ENTRIES_PER_PAGE) {
            pages.add(new ArrayList<>(flat.subList(i, Math.min(i + ENTRIES_PER_PAGE, flat.size()))));
        }

        ItemStack clipboard = new ItemStack(clipboardItem);
        clipboard.set(AllDataComponents.CLIPBOARD_CONTENT,
                new ClipboardContent(
                        ClipboardOverrides.ClipboardType.WRITTEN, pages, true));
        MutableComponent title = Component.literal("Materials: " + placementName);
        title.setStyle(Style.EMPTY.withItalic(false));
        clipboard.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, title);

        if (!player.getInventory().add(clipboard)) {
            player.drop(clipboard, false);
        }
        player.displayClientMessage(Component.literal("§a[L2C] Clipboard with "
                + entries.size() + " materials (" + total + " blocks) in your inventory. "
                + "Hand it to the Stock keeper."), false);
    }
}
