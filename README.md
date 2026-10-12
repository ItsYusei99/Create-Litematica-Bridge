# Create: Litematica Bridge

Sends Litematica (Forgematica) placement material lists to the server, which hands you a
Create clipboard — identical to the one printed by the Schematicannon — ready to hand to
the Stock keeper for ordering from your warehouse.

## How it works

1. Open a Litematica placement's configuration screen and click **"Materials → Clipboard"**
   (top right), or run `/l2create [<placement name>]`.
2. If the material list isn't calculated yet, it is counted in the background and the
   request is sent automatically when done.
3. The server builds a Create clipboard (WRITTEN type, 7 entries per page, total amounts)
   and puts it in your inventory.
4. Hand the clipboard to the Stock keeper to order everything from your logistics network.

## Exporting to the Schematicannon

Click **"Export schematic"** in the same placement screen (no server needed). This snapshots
the placement into a vanilla `.nbt` file inside the `schematics/` folder
(`<name>.nbt`), ready to load in the Schematic Table for the Schematicannon — no external
converter required. The chat message includes the placement origin coordinates and size
to help align the cannon.

## Verify and reorder

Click **"Verify → Clipboard"** (or `/l2create verify [<name>]`) to compare the placement
against the built world and order a clipboard with only the missing or wrong blocks.

## Ignore list

Blocks/items listed in `config/l2create-ignore.txt` (one id per line) are skipped in
clipboards, verify orders and schematic exports. Manage it with
`/l2create ignore list|add <id>|remove <id>`.

## Batch processing

`/l2create all` sends clipboard orders and exports schematics for every loaded placement.
`/l2create export [<name>]` exports a single placement without opening its screen.

The mod is split by necessity: the material list only exists on the client (Litematica),
while only the server can give you items. A single jar works on both sides.

## Requirements

- Minecraft 1.21.1, NeoForge 21.1.x
- [Create](https://modrinth.com/mod/create) 6.0.x (client **and** server)
- [Forgematica](https://modrinth.com/mod/forgematica) (client only, for the material list)

## Building

This repository does **not** redistribute third-party mods. To compile, download the exact
jars below and place them in the `libs/` folder (same filenames):

- `forgematica-0.4.1+mc1.21.1.jar`
- `create-1.21.1-6.0.10.jar`
- `mafglib-0.4.3+mc1.21.1.jar` (MaLiLib, compile-only)

Then run with Gradle 8.13+:

```sh
gradle build
```

The jar will be in `build/libs/`. Install the same jar on client and server.

## Notes / limitations

- Totals are ordered (not "missing" amounts).
- The Stock keeper orders everything on the clipboard; `checked` flags are display-only.
- Requires Create's clipboard item (`create:clipboard`) to exist.

## License

MIT — see [LICENSE](LICENSE).
