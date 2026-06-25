# Horizon Client

A lightweight Fabric client mod for Minecraft **1.21.4**, built with a modular architecture and a clean in-game GUI.

---

## Features

### ðŸ”´ PvP
| Module | Description |
|---|---|
| **Aim Assist** | Smoothly nudges your crosshair toward nearby players with configurable FOV, speed, and aim-at position |
| **Trigger Bot** | Automatically attacks when a player is under your crosshair; supports weapon-specific delays and crit-only modes |
| **Crystal Macro** | Automates end-crystal placing and breaking with configurable CPS and obsidian placement |
| **Anchor Macro** | Automates respawn anchor usage with configurable delays and totem slot management |
| **Hover Totem** | Automatically equips a totem of undying to your offhand when you hover over one in your inventory |
| **Inv Totem** | Automatically moves totems to your offhand from inventory with legit/blatant timing modes |

### ðŸŸ¡ Render / ESP
| Module | Description |
|---|---|
| **Storage ESP** | Highlights chests, ender chests, barrels, shulkers, hoppers, dispensers, droppers, and pistons |
| **Ore ESP** | Highlights ores through walls with per-ore-type toggles and async chunk scanning |
| **Player ESP** | Draws boxes and tracers around other players |
| **Spawner ESP** | Highlights mob spawners with configurable fill and tracers |
| **Item ESP** | Labels dropped items in the world with name, count, and icon |
| **Bedrock Holes** | Highlights bedrock-floor holes for safe crystal PvP positioning |

### ðŸŸ¢ Movement
| Module | Description |
|---|---|
| **AutoSprint** | Automatically sprints when moving forward |
| **FreeCam** | Detaches your camera from your body; supports camera-position interactions |
| **FreeLook** | Lets you look around freely without changing your movement direction |

### ðŸ”µ Misc
| Module | Description |
|---|---|
| **FullBright** | Applies permanent night vision for full brightness in any lighting |
| **Hand View** | Customizes hand FOV and swing speed |
| **Name Protect** | Replaces your username (and optionally other players' names) with a custom string |
| **Skin Protect** | Hides your real skin from the client |
| **Fake Scoreboard** | Replaces scoreboard sidebar and tab list values (balance, shards, kills, etc.) with custom text |
| **Fake Pay** | Intercepts `/pay` commands to show a fake payment confirmation client-side |
| **Home Reset** | Automates the home reset sequence for a selected home slot |

### ðŸŸ£ Client
| Module | Description |
|---|---|
| **HUD** | Renders a watermark, FPS, ping, coordinates, active module list, and a player radar |
| **Region Map** | Draws a mini region map overlay showing your position across server regions |

### âšª Debug
| Module | Description |
|---|---|
| **Sus Chunk Finder** | Flags chunks with high geode overlap density |
| **Prime Chunk Finder** | Flags chunks with unusual piston packet activity |
| **Activity Debug** | Visualizes chunk activity signals around the player |
| **Spawner Debug** | Tracks and highlights newly loaded spawner chunks |
| **Block Entity Debug** | Highlights all block entities in loaded chunks |
| **Relog Debug** | Alerts (or auto-disconnects) when the player falls below a set Y level |
| **Render Debug** | Cycles render distance to force a chunk re-render below a set Y level |

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
git clone <repo-url>
cd horizon-project
./gradlew build
```

The output JAR will be in `build/libs/`.

---

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.4.
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) and place it in your `mods` folder.
3. Drop the Horizon JAR into your `mods` folder.
4. Launch the game.

---

## Usage

Press **Right Shift** in-game to open the Horizon GUI.

- **Navigate categories** using the sidebar tabs (Render, Movement, Misc, Client, PvP, Debug).
- **Toggle a module** by clicking its name.
- **Open settings** by clicking the arrow next to a module.
- **Bind a keybind** via the Bind button in the module's settings panel.
- Settings are saved automatically on game exit.

---

## License

MIT
