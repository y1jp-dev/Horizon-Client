# Horizon Client v3

A lightweight, modular Fabric client mod for **Minecraft 1.21.4** built on Java 21.  
Open the GUI with **Right Shift** and toggle any module on/off with custom keybinds.

---

## Requirements

| Dependency | Version |
|---|---|
| Minecraft | 1.21.4 |
| Fabric Loader | â‰¥ 0.16.0 |
| Fabric API | 0.110.5+1.21.4 |
| Java | 21+ |

---

## Building

```bash
git clone <your-repo-url>
cd horizon-v3
./gradlew build
```

The compiled `.jar` will be in `build/libs/`. Drop it into your `.minecraft/mods/` folder alongside Fabric API.

---

## Modules

### Combat

| Module | Description |
|---|---|
| **Aim Assist** | Soft aim towards nearby players. Configurable radius, sticky aim, and visibility check. |
| **Trigger Bot** | Automatically attacks when your crosshair is on a player or mob. Supports any item, randomised delay, and works while right-clicking. |
| **Anchor Macro** | Automates respawn anchor usage. |
| **Crystal Macro** | Automates end crystal placement and detonation. |
| **Hover Totem** | Keeps a totem of undying in your offhand by moving one from your inventory when needed. Optionally stocks a hotbar slot too. |
| **Inv Totem** | Auto-equips totems from your inventory, with legit (random slot) and blatant (first slot) modes. |

### Movement

| Module | Description |
|---|---|
| **Auto Sprint** | Automatically sprints whenever possible (respects hunger and sneaking). |
| **Freecam** | Detaches your camera from your player. Configurable speed and camera-position interaction. |
| **Freelook** | Lets you look around freely without rotating your player. |

### Visual / ESP

| Module | Description |
|---|---|
| **Ore ESP** | Highlights ores through walls with configurable per-ore toggles. |
| **Player ESP** | Draws boxes around players through walls. |
| **Item ESP** | Highlights dropped items on the ground. |
| **Storage ESP** | Highlights chests, barrels, shulker boxes, and other storage blocks. |
| **Spawner ESP** | Highlights mob spawners. |
| **Bedrock Holes** | Highlights bedrock-floored holes (useful for crystal PvP). |
| **Sus Chunk** | Marks suspicious (recently-modified) chunks. |
| **Prime Chunk** | Highlights prime (16Â³-aligned) chunk corners. |
| **Full Bright** | Applies permanent night-vision so everything is lit up. |
| **Hand View Model** | Adjusts the position and scale of your held-item hand model. |
| **HUD** | On-screen display showing active modules, coordinates, and a watermark. |

### Privacy

| Module | Description |
|---|---|
| **Name Protect** | Replaces your username (and optionally other players' names) with a custom fake name. |
| **Skin Protect** | Hides your skin from being rendered to others. |
| **Fake Scoreboard** | Spoofs your scoreboard stat values with fake numbers. |

### Utility

| Module | Description |
|---|---|
| **Home Reset** | Utility for automating home-reset sequences. |
| **Horizon GUI** | Opens the in-game module menu (default keybind: **Right Shift**). |

### Debug

| Module | Description |
|---|---|
| **Horizon Debug** | Internal debug renderer for development. |
| **Activity Debug** | Visualises player activity data. |
| **Block Entity Debug** | Renders debug info on block entities. |
| **Spawner Debug** | Shows spawner internals. |
| **Deepslate Debug** | Debug overlay for deepslate layer. |
| **Relog Debug** | Debug tool for relog-related state. |

---

## Configuration

Settings are saved automatically and persist across sessions. Each module supports a custom keybind that can be set from the GUI. Config files are stored in your Minecraft run directory.
