package com.itsyusei.l2c;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Client side ignore list, one block/item id per line.
 * Applies to clipboard materials, verify orders and schematic exports.
 */
@OnlyIn(Dist.CLIENT)
public final class IgnoreList {
    private static final String FILE_NAME = "l2create-ignore.txt";

    private IgnoreList() {
    }

    public static File file() {
        File dir = new File(Minecraft.getInstance().gameDirectory, "config");
        dir.mkdirs();
        return new File(dir, FILE_NAME);
    }

    public static Set<String> load() {
        Set<String> ids = new HashSet<>();
        File file = file();
        if (!file.isFile()) {
            save(ids, "# One block or item id per line, e.g. minecraft:dirt");
            return ids;
        }
        try {
            for (String line : Files.readAllLines(file.toPath())) {
                String id = line.trim();
                if (!id.isEmpty() && !id.startsWith("#")) {
                    ids.add(id);
                }
            }
        } catch (IOException ignored) {
        }
        return ids;
    }

    public static void save(Set<String> ids, String headerComment) {
        List<String> lines = new ArrayList<>();
        if (headerComment != null) {
            lines.add(headerComment);
        }
        List<String> sorted = new ArrayList<>(ids);
        java.util.Collections.sort(sorted);
        lines.addAll(sorted);
        try {
            Files.write(file().toPath(), lines);
        } catch (IOException ignored) {
        }
    }

    public static boolean isValidId(String id) {
        return id != null && id.contains(":")
                && ResourceLocation.tryParse(id.trim()) != null;
    }

    /** Matches by item id, or by block id for block items. */
    public static boolean isIgnored(ItemStack stack, Set<String> ids) {
        if (stack.isEmpty() || ids.isEmpty()) {
            return false;
        }
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (ids.contains(itemId)) {
            return true;
        }
        if (stack.getItem() instanceof BlockItem blockItem) {
            String blockId = BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()).toString();
            return ids.contains(blockId);
        }
        return false;
    }

    /** Matches a vanilla structure palette entry name, e.g. "minecraft:stone". */
    public static boolean isIgnoredPaletteName(String name, Set<String> ids) {
        return name != null && ids.contains(name);
    }
}
