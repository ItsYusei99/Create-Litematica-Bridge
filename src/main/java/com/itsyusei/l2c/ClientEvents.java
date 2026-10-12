package com.itsyusei.l2c;

import com.mojang.brigadier.arguments.StringArgumentType;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.gui.GuiPlacementConfiguration;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.interfaces.ICompletionListener;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
public class ClientEvents {
    private static final String CLIPBOARD_BUTTON_TEXT = "Materials \u2192 Clipboard";
    private static final String EXPORT_BUTTON_TEXT = "Export schematic";
    private static final Set<String> PENDING = new HashSet<>();

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof GuiPlacementConfiguration placementScreen) {
            SchematicPlacement placement = placementScreen.getSchematicPlacement();
            if (placement == null) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            int sw = mc.getWindow().getGuiScaledWidth();
            IButtonActionListener listener = (btn, mouseButton) -> startClipboardFlow(placement);
            ButtonGeneric button = new ButtonGeneric(sw - 178, 4, 174, 20, CLIPBOARD_BUTTON_TEXT,
                    "Ask the server for a clipboard with this placement's materials");
            ((GuiBase) placementScreen).addButton(button, listener);
            IButtonActionListener exportListener = (btn, mouseButton) -> SchematicExporter.exportPlacement(placement);
            ButtonGeneric exportButton = new ButtonGeneric(sw - 178, 26, 174, 20, EXPORT_BUTTON_TEXT,
                    "Save this placement as a vanilla .nbt schematic for the Schematic Table");
            ((GuiBase) placementScreen).addButton(exportButton, exportListener);
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("l2create")
                .requires(src -> true)
                .executes(ctx -> clipboardFirst())
                .then(Commands.argument("placement", StringArgumentType.greedyString())
                        .executes(ctx -> clipboardNamed(StringArgumentType.getString(ctx, "placement")))));
    }

    private static int clipboardFirst() {
        List<SchematicPlacement> placements =
                DataManager.getSchematicPlacementManager().getAllSchematicsPlacements();
        if (placements.isEmpty()) {
            tell(Component.literal("§c[L2C] No placements loaded"));
            return 0;
        }
        if (placements.size() > 1) {
            StringBuilder names = new StringBuilder();
            for (SchematicPlacement p : placements) {
                if (names.length() > 0) {
                    names.append(", ");
                }
                names.append(p.getName());
            }
            tell(Component.literal("§e[L2C] Multiple placements loaded, specify one: /l2create <name> ("
                    + names + ")"));
            return 0;
        }
        startClipboardFlow(placements.get(0));
        return 1;
    }

    private static int clipboardNamed(String name) {
        List<SchematicPlacement> placements =
                DataManager.getSchematicPlacementManager().getAllSchematicsPlacements();
        for (SchematicPlacement p : placements) {
            if (p.getName().equalsIgnoreCase(name.trim())) {
                startClipboardFlow(p);
                return 1;
            }
        }
        tell(Component.literal("§c[L2C] Placement not found: " + name));
        return 0;
    }

    static void startClipboardFlow(SchematicPlacement placement) {
        MaterialListBase list = placement.getMaterialList();
        List<MaterialListEntry> materials = list.getMaterialsAll();
        if (materials != null && !materials.isEmpty()) {
            sendRequest(placement);
            return;
        }
        if (!PENDING.add(placement.getName())) {
            tell(Component.literal("§e[L2C] Already calculating materials..."));
            return;
        }
        list.setCompletionListener(new ICompletionListener() {
            @Override
            public void onTaskCompleted() {
                PENDING.remove(placement.getName());
                Minecraft.getInstance().execute(() -> sendRequest(placement));
            }

            @Override
            public void onTaskAborted() {
                PENDING.remove(placement.getName());
                Minecraft.getInstance().execute(() ->
                        tell(Component.literal("§c[L2C] Material calculation cancelled")));
            }
        });
        list.reCreateMaterialList();
        tell(Component.literal("§e[L2C] Calculating materials for '" + placement.getName() + "'..."));
    }

    private static void sendRequest(SchematicPlacement placement) {
        List<MaterialListEntry> materials = placement.getMaterialList().getMaterialsAll();
        if (materials == null || materials.isEmpty()) {
            tell(Component.literal("§c[L2C] Material list is empty"));
            return;
        }
        List<MaterialsOrderPacket.Entry> payload = new ArrayList<>(materials.size());
        for (MaterialListEntry entry : materials) {
            ItemStack icon = entry.getStack().copy();
            if (icon.isEmpty()) {
                continue;
            }
            int amount = entry.getCountTotal();
            if (amount < 1) {
                continue;
            }
            icon.setCount(1);
            payload.add(new MaterialsOrderPacket.Entry(icon, amount));
        }
        if (payload.isEmpty()) {
            tell(Component.literal("§c[L2C] Nothing to order"));
            return;
        }
        PacketDistributor.sendToServer(new MaterialsOrderPacket(placement.getName(), payload));
        tell(Component.literal("§e[L2C] Order sent: " + payload.size()
                + " materials. The clipboard will arrive in your inventory."));
    }

    private static void tell(Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(message, false);
        }
    }
}
