package com.itsyusei.l2c;

import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.io.File;

/**
 * Client side: snapshots a placement from the schematic world into a vanilla
 * structure .nbt file inside the Create schematics folder, ready to load in
 * the Schematic Table. No server involvement needed.
 */
@OnlyIn(Dist.CLIENT)
public final class SchematicExporter {
    private SchematicExporter() {
    }

    public static void exportPlacement(SchematicPlacement placement) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        try {
            WorldSchematic world = SchematicWorldHandler.getSchematicWorld();
            if (world == null) {
                tell(mc, Component.literal("§c[L2C] Schematic world not available"));
                return;
            }
            Box box = placement.getEclosingBox();
            BlockPos p1 = box.getPos1();
            BlockPos p2 = box.getPos2();
            BlockPos min = new BlockPos(Math.min(p1.getX(), p2.getX()),
                    Math.min(p1.getY(), p2.getY()),
                    Math.min(p1.getZ(), p2.getZ()));
            BlockPos max = new BlockPos(Math.max(p1.getX(), p2.getX()),
                    Math.max(p1.getY(), p2.getY()),
                    Math.max(p1.getZ(), p2.getZ()));
            Vec3i size = new Vec3i(max.getX() - min.getX() + 1,
                    max.getY() - min.getY() + 1,
                    max.getZ() - min.getZ() + 1);
            if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0) {
                tell(mc, Component.literal("§c[L2C] Placement is empty"));
                return;
            }
            // Same pattern Litematica itself uses for vanilla export.
            StructureTemplate template = new StructureTemplate();
            template.fillFromWorld(world, min, size, false, Blocks.STRUCTURE_VOID);
            CompoundTag nbt = template.save(new CompoundTag());
            stripIgnored(nbt, IgnoreList.load());
            File dir = new File(mc.gameDirectory, "schematics");
            if (!dir.isDirectory() && !dir.mkdirs()) {
                tell(mc, Component.literal("§c[L2C] Could not create schematics folder"));
                return;
            }
            String safe = placement.getName().replaceAll("[^a-zA-Z0-9._-]", "_");
            if (safe.isEmpty()) {
                safe = "placement";
            }
            File out = new File(dir, safe + ".nbt");
            NbtIo.writeCompressed(nbt, out.toPath());
            BlockPos origin = placement.getOrigin();
            tell(mc, Component.literal("§a[L2C] Exported schematics/" + out.getName()
                    + " (" + size.getX() + "x" + size.getY() + "x" + size.getZ()
                    + "). Origin: " + origin.getX() + ", " + origin.getY() + ", " + origin.getZ()
                    + ". Load it in the Schematic Table."));
        } catch (Exception e) {
            tell(mc, Component.literal("§c[L2C] Export failed: " + e.getMessage()));
        }
    }

    /**
     * Removes blocks whose palette id is in the ignore list, then prunes the
     * palette and reindexes. Operates on a vanilla structure tag in place.
     */
    static void stripIgnored(CompoundTag nbt, java.util.Set<String> ids) {
        if (ids.isEmpty() || !nbt.contains("palette") || !nbt.contains("blocks")) {
            return;
        }
        ListTag palette = nbt.getList("palette", Tag.TAG_COMPOUND);
        boolean[] drop = new boolean[palette.size()];
        boolean any = false;
        for (int i = 0; i < palette.size(); i++) {
            String name = palette.getCompound(i).getString("Name");
            if (IgnoreList.isIgnoredPaletteName(name, ids)) {
                drop[i] = true;
                any = true;
            }
        }
        if (!any) {
            return;
        }
        int[] remap = new int[palette.size()];
        ListTag newPalette = new ListTag();
        for (int i = 0; i < palette.size(); i++) {
            if (drop[i]) {
                remap[i] = -1;
            } else {
                remap[i] = newPalette.size();
                newPalette.add(palette.get(i));
            }
        }
        ListTag blocks = nbt.getList("blocks", Tag.TAG_COMPOUND);
        ListTag newBlocks = new ListTag();
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag info = blocks.getCompound(i);
            int oldState = info.getInt("state");
            if (oldState < 0 || oldState >= remap.length || remap[oldState] < 0) {
                continue;
            }
            info.putInt("state", remap[oldState]);
            newBlocks.add(info);
        }
        nbt.put("palette", newPalette);
        nbt.put("blocks", newBlocks);
    }

    private static void tell(Minecraft mc, Component message) {
        if (mc.player != null) {
            mc.player.displayClientMessage(message, false);
        }
    }
}
