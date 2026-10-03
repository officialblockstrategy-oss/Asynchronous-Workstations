# Project: Asynchronous Workstations (Fabric 1.21.1)

## Environment (never change)
- Minecraft 1.21.1, Fabric, Yarn mappings 1.21.1+build.3, Java 21, Loom 1.9.2
- Mod ID: asynch-stations. Package: com.blockstrategy.asynchstations
- If unsure a class or method exists in 1.21.1 Yarn, say so instead of guessing.
- Use ./gradlew build to check that code compiles. Don't run runClient. I'll launch the game myself

## Code style
- Write code the way an experienced Java developer would by hand: plain,
  readable, and no more complicated than the task needs.
- Use clear, specific names. No generic names like "data", "handler", "util",
  or "manager" unless they truly fit.
- Comment only when explaining WHY something is done or a Minecraft quirk.
  Do not comment what the code obviously does, and no banner or section comments.
- No unnecessary abstractions, interfaces, builders, or design patterns.
  Prefer the simplest approach that works, and match the style of existing files.
- Keep methods short and files focused. Don't add features I didn't ask for.
- No emojis in code or comments. No excessive null-checks or defensive code.
- Use Fabric's and Minecraft's own conventions (static registration classes,
  Identifier helpers, etc.).

## How to work with me
- I'm a beginner. Work in small steps and say which file you're editing.
- Don't rewrite whole files. Make the smallest change that works.
- Explain what you changed in plain language after the code.

## Project vision
This will act as one piece of a larger survival/RPG modpack. In this mod, vanilla workstations work asynchronously,
like furnaces. Players start a craft, walk away, and collect the result later.
Timers run on the server and only while the chunk is loaded.

### Core design (decided)
- Block entities store one tray per player UUID. Each player sees only their
  own tray, so several players can use one table at once.
- The server owns all state. Clients only render. Never trust client-reported
  results without validating them.
- Each player may run 2 crafts at once. A third within 16 blocks of their
  other active tables is queued, not refused. Status text under the output
  slot explains the state ("Crafting: 2m 6s", "Queued: ...").
- Craft time comes from CraftTimes.java: per-item overrides first, then item
  class (so modded items work), then a default.
- If a table is broken, every tray's contents drop as items.
- The async table is displayed as "Crafting Table" on purpose.

### Planned stations
- Crafting table: vanilla-like 3x3 GUI and recipe book. Taking the result
  starts a timed craft instead of giving the item.
- Anvil: all alloy tools, weapons, and armor are crafted here (detected by
  item class). Starting a craft runs a timing QTE: a needle crosses a bar
  with 5 zones (100/75/50/25/0 percent). The green zone shrinks and moves
  after each hit. Hit count scales with material cost. Final accuracy
  multiplies the item's max durability.
- Smithing table and grindstone reuse the anvil QTE. Loom and stonecutter
  use the queue with no QTE.
- Enchanting table (last): arcane-themed QTE, rounds equal the enchant level.
  Failure damages the player and item. Success applies the enchant and gives
  Absorption.
- Furnace, smoker, blast furnace, campfire, brewing stand: unchanged.
- Undecided: depend on Fuzs's mods vs build our own versions.