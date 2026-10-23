# Arda Client

Arda Client is a Fabric mod with quality-of-life tools for the Arda server. It works on its own, and adds extra features
when [Meteor Client](https://meteorclient.com/) is installed alongside it.

## Features

- **Quick wheel** - Hold middle mouse to open a radial menu for the Encyclopedia, server actions, and your own command
  shortcuts.
- **Command shortcuts** - Create, edit, and delete your own one-click commands from the wheel.
- **Dungeon panel** - A compact panel showing open, active, and cooldown status for all 12 dungeon tiers. Updates from
  `/dungeon_info` and dungeon announcements in chat. Individual tiers can be hidden.
- **Currency counter** - Totals the Black Gems, Forged Tungsten, Zircon, and Bloodstones in your inventory, shown as
  whole Black Gems plus change in Bloodstones.
- **Arda Encyclopedia** - A searchable catalog of item prices, including upgrade costs.
- **Item tooltips and inventory worth** - Shows the price of known items in their tooltip and totals the value of your
  inventory.
- **Encyclopedia drafts** - Add your own local price entries from a held item.

Both panels start in the top-left corner and can be moved and scaled from **HUD Settings** (Quick wheel > Commands >
HUD Settings).

## Requirements

- Minecraft 26.2
- [Fabric Loader](https://fabricmc.net/use/) 0.19.3 or newer
- [Fabric API](https://modrinth.com/mod/fabric-api) 0.158.0 or newer
- Java 25
- Optional: [Meteor Client](https://meteorclient.com/) for 26.2

## Installation

1. Install Fabric Loader for Minecraft 26.2.
2. Download the latest `arda-client` JAR from the
   [releases page](https://github.com/GnJake/Arda-Client/releases).
3. Put the Arda Client JAR and Fabric API in your `.minecraft/mods` folder. Add Meteor Client too if you use it.
4. Launch the game with the Fabric profile.

If you previously used the standalone Arda Companion mod, remove it from your `mods` folder. Arda Client includes the
same features and copies your existing settings on first launch.

## Controls

All controls can be changed in **Options > Controls > Key Binds** under **Arda Client**.

| Action                 | Default      |
|------------------------|--------------|
| Hold quick wheel       | Middle mouse |
| Open Arda Encyclopedia | Unbound      |
| Toggle Dungeon HUD     | Unbound      |

## Configuration

Settings are saved automatically in your `.minecraft/config` folder:

- `arda-client.json` - Command shortcuts, HUD settings, positions, and scale.
- `arda-client/encyclopedia-drafts.json` - Your local Encyclopedia drafts.

## Building from source

```bash
git clone https://github.com/GnJake/Arda-Client
cd Arda-Client
./gradlew build
```

The JAR is written to `build/libs`.

## License

Arda Client is available under the CC0 license.
