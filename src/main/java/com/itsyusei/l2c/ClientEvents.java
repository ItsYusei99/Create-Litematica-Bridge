package com.itsyusei.l2c;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
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
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
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
    private static final String VERIFY_BUTTON_TEXT = "Verify \u2192 Clipboard";
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
            GuiBase gui = (GuiBase) placementScreen;
            IButtonActionListener clipboardListener = (btn, mouseButton) -> startClipboardFlow(placement);
            gui.addButton(new ButtonGeneric(sw - 178, 4, 174, 20, CLIPBOARD_BUTTON_TEXT,
                    "Ask the server for a clipboard with this placement's materials"), clipboardListener);
            IButtonActionListener exportListener = (btn, mouseButton) -> SchematicExporter.exportPlacement(placement);
            gui.addButton(new ButtonGeneric(sw - 178, 26, 174, 20, EXPORT_BUTTON_TEXT,
                    "Save this placement as a vanilla .nbt schematic for the Schematic Table"), exportListener);
            IButtonActionListener verifyListener = (btn, mouseButton) -> VerifyOrder.startVerifyFlow(placement);
            gui.addButton(new ButtonGeneric(sw - 178, 48, 174, 20, VERIFY_BUTTON_TEXT,
                    "Verify against the world and order only the missing blocks"), verifyListener);
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("l2create")
                .requires(src -> true)
                .executes(ctx -> clipboardFirst())
                .then(Commands.literal("verify")
                        .executes(ctx -> verifyFirst())
                        .then(Commands.argument("placement", StringArgumentType.greedyString())
                                .executes(ctx -> verifyNamed(StringArgumentType.getString(ctx, "placement")))))
                .then(Commands.literal("export")
                        .executes(ctx -> exportFirst())
                        .then(Commands.argument("placement", StringArgumentType.greedyString())
                                .executes(ctx -> exportNamed(StringArgumentType.getString(ctx, "placement")))))
                .then(Commands.literal("all")
                        .executes(ClientEvents::processAll))
                .then(Commands.literal("ignore")
                        .then(Commands.literal("list")
                                .executes(ClientEvents::ignoreList))
                        .then(Commands.literal("add")
                                .then(Commands.argument("id", StringArgumentType.string())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                                knownIds().stream(), b))
                                        .executes(ctx -> ignoreAdd(StringArgumentType.getString(ctx, "id")))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("id", StringArgumentType.string())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                                IgnoreList.load().stream(), b))
                                        .executes(ctx -> ignoreRemove(StringArgumentType.getString(ctx, "id"))))))
                .then(Commands.argument("placement", StringArgumentType.greedyString())
                        .executes(ctx -> clipboardNamed(StringArgumentType.getString(ctx, "placement")))));
    }

    private static List<SchematicPlacement> placements() {
        return DataManager.getSchematicPlacementManager().getAllSchematicsPlacements();
    }

    private static SchematicPlacement findPlacement(String name) {
        for (SchematicPlacement p : placements()) {
            if (p.getName().equalsIgnoreCase(name.trim())) {
                return p;
            }
        }
        return null;
    }

    private static String allNames() {
        StringBuilder names = new StringBuilder();
        for (SchematicPlacement p : placements()) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(p.getName());
        }
        return names.toString();
    }

    private static int clipboardFirst() {
        List<SchematicPlacement> all = placements();
        if (all.isEmpty()) {
            tell(Component.literal("§c[L2C] No placements loaded"));
            return 0;
        }
        if (all.size() > 1) {
            tell(Component.literal("§e[L2C] Multiple placements loaded, specify one: /l2create <name> ("
                    + allNames() + ")"));
            return 0;
        }
        startClipboardFlow(all.get(0));
        return 1;
    }

    private static int clipboardNamed(String name) {
        SchematicPlacement placement = findPlacement(name);
        if (placement == null) {
            tell(Component.literal("§c[L2C] Placement not found: " + name));
            return 0;
        }
        startClipboardFlow(placement);
        return 1;
    }

    private static int verifyFirst() {
        List<SchematicPlacement> all = placements();
        if (all.isEmpty()) {
            tell(Component.literal("§c[L2C] No placements loaded"));
            return 0;
        }
        if (all.size() > 1) {
            tell(Component.literal("§e[L2C] Multiple placements loaded, specify one: /l2create verify <name> ("
                    + allNames() + ")"));
            return 0;
        }
        VerifyOrder.startVerifyFlow(all.get(0));
        return 1;
    }

    private static int verifyNamed(String name) {
        SchematicPlacement placement = findPlacement(name);
        if (placement == null) {
            tell(Component.literal("§c[L2C] Placement not found: " + name));
            return 0;
        }
        VerifyOrder.startVerifyFlow(placement);
        return 1;
    }

    private static int exportFirst() {
        List<SchematicPlacement> all = placements();
        if (all.isEmpty()) {
            tell(Component.literal("§c[L2C] No placements loaded"));
            return 0;
        }
        if (all.size() > 1) {
            tell(Component.literal("§e[L2C] Multiple placements loaded, specify one: /l2create export <name> ("
                    + allNames() + ")"));
            return 0;
        }
        SchematicExporter.exportPlacement(all.get(0));
        return 1;
    }

    private static int exportNamed(String name) {
        SchematicPlacement placement = findPlacement(name);
        if (placement == null) {
            tell(Component.literal("§c[L2C] Placement not found: " + name));
            return 0;
        }
        SchematicExporter.exportPlacement(placement);
        return 1;
    }

    private static int processAll(CommandContext<CommandSourceStack> ctx) {
        List<SchematicPlacement> all = placements();
        if (all.isEmpty()) {
            tell(Component.literal("§c[L2C] No placements loaded"));
            return 0;
        }
        for (SchematicPlacement placement : all) {
            startClipboardFlow(placement);
            SchematicExporter.exportPlacement(placement);
        }
        return 1;
    }

    private static int ignoreList(CommandContext<CommandSourceStack> ctx) {
        Set<String> ids = IgnoreList.load();
        if (ids.isEmpty()) {
            tell(Component.literal("§e[L2C] Ignore list is empty (config/l2create-ignore.txt)"));
        } else {
            tell(Component.literal("§e[L2C] Ignored: " + String.join(", ", ids)));
        }
        return 1;
    }

    private static int ignoreAdd(String id) {
        if (!IgnoreList.isValidId(id)) {
            tell(Component.literal("§c[L2C] Invalid id, use namespace:path, e.g. minecraft:dirt"));
            return 0;
        }
        Set<String> ids = IgnoreList.load();
        if (ids.add(id.trim())) {
            IgnoreList.save(ids, "# One block or item id per line, e.g. minecraft:dirt");
            tell(Component.literal("§a[L2C] Now ignoring " + id.trim()));
        } else {
            tell(Component.literal("§e[L2C] Already ignoring " + id.trim()));
        }
        return 1;
    }

    private static int ignoreRemove(String id) {
        Set<String> ids = IgnoreList.load();
        if (ids.remove(id.trim())) {
            IgnoreList.save(ids, "# One block or item id per line, e.g. minecraft:dirt");
            tell(Component.literal("§a[L2C] No longer ignoring " + id.trim()));
        } else {
            tell(Component.literal("§e[L2C] Was not ignoring " + id.trim()));
        }
        return 1;
    }

    private static Set<String> knownIds() {
        Set<String> ids = new HashSet<>();
        ids.add("minecraft:dirt");
        ids.add("minecraft:stone");
        ids.add("minecraft:cobblestone");
        ids.add("minecraft:water");
        ids.add("minecraft:lava");
        ids.add("minecraft:air");
        ids.add("minecraft:tall_grass");
        return ids;
    }

    static void startClipboardFlow(SchematicPlacement placement) {
        MaterialListBase list = placement.getMaterialList();
        List<MaterialListEntry> materials = list.getMaterialsAll();
        if (materials != null && !materials.isEmpty()) {
            sendClipboard(placement);
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
                Minecraft.getInstance().execute(() -> sendClipboard(placement));
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

    private static void sendClipboard(SchematicPlacement placement) {
        List<MaterialListEntry> materials = placement.getMaterialList().getMaterialsAll();
        if (materials == null || materials.isEmpty()) {
            tell(Component.literal("§c[L2C] Material list is empty"));
            return;
        }
        Set<String> ignored = IgnoreList.load();
        List<MaterialsOrderPacket.Entry> payload = new ArrayList<>(materials.size());
        for (MaterialListEntry entry : materials) {
            ItemStack icon = entry.getStack().copy();
            if (icon.isEmpty() || IgnoreList.isIgnored(icon, ignored)) {
                continue;
            }
            int amount = entry.getCountTotal();
            if (amount < 1) {
                continue;
            }
            icon.setCount(1);
            payload.add(new MaterialsOrderPacket.Entry(icon, amount));
        }
        sendEntries(placement.getName(), payload, "materials");
    }

    static void sendEntries(String placementName, List<MaterialsOrderPacket.Entry> payload, String what) {
        if (payload.isEmpty()) {
            tell(Component.literal("§c[L2C] Nothing to order"));
            return;
        }
        PacketDistributor.sendToServer(new MaterialsOrderPacket(placementName, payload));
        tell(Component.literal("§e[L2C] Order sent: " + payload.size()
                + " " + what + ". The clipboard will arrive in your inventory."));
    }

    private static void tell(Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(message, false);
        }
    }
}
