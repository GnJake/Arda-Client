# Arda Client

Arda Client is a Fabric mod with quality-of-life tools for the Arda server. It works on its own, and adds extra features
when [Meteor Client](https://meteorclient.com/) is installed alongside it.

## Features

- **Quick wheel** - Hold middle mouse to open a radial menu of 4 to 16 slots for server actions, Arda Client's screens, and
  your own command shortcuts. **Edit Wheel** (in Commands) chooses what each slot holds, and sets the wheel's size,
  labels and icons.
- **Command shortcuts** - Create, edit, and delete your own one-click commands from the wheel.
- **Profit estimator** - Posts an estimated profit in chat when you start or finish a dungeon, and when you finish a
  skirmish, and adds each skirmish's estimate to `/skirmish info`. Dungeon estimates use your party size from the party scoreboard. The Profit screen lists estimates for
  every dungeon level with 1 to 5 players, and every skirmish level. Estimates are player averages: a skirmish pays
  about 2.25 Black Gems per level, and a dungeon about 2.25 Black Gems per level plus 2.25 and 2.25 Mithril per level,
  for each player.
- **Skirmish and mirage waypoints** - Skirmish announcements, `/skirmish info`, and Mirage Encampment announcements get
  a waypoint link. Click it to add a waypoint
  to Xaero's Minimap (in a "Skirmish" or "Mirage" waypoint set, also shown on Xaero's World Map) or Meteor Client. Arda only gives
  a skirmish's X and Z, so waypoints go at Y 80 by default. **Waypoint Settings** (on the wheel, or the Profit
  Estimator's Skirmishes page) chooses the mod and height and removes old skirmish waypoints.
- **Structures** - Harad's camps, outposts, towers and about 300 tunnel spawners come built in. **Completion** marks a
  tunnel spawner done when you finish it, until Harad refreshes. With Meteor, the **tunnel-xray** module outlines tunnel
  spawners through walls. **Add Camps** (or Outposts, Tunnels, Towers) puts that list on Xaero's map in a waypoint set
  of the same name, and **Add All** puts every structure in an "All Structures" set.
- **Towers Event** - A panel shows which clan holds each tower and how many each clan has, then the final standings
  for five minutes after. The Towers tab in Structures shows each
  logged tower's holder too. A **TOWERS** wheel slot runs `/towersinfo holders`, which fills in the holders any time.
  **Towers** in HUD Settings shows the panel only during the event, always, or never.
- **Tunnel pathfinding** - With **Path** on, a particle trail shows the best walking path to the nearest tunnel spawner you
  haven't completed yet. With Meteor's **tunnel-xray** module on, the path is drawn as highlighted floor blocks instead.
- **Currency log** - The currency or Mithril tally in full: totals and hourly rates for each period, the last 12 hours hour by hour, and
  your recent earnings with where each came from.
- **Dungeon panel** - A compact panel showing open, active, and cooldown status for all 12 dungeon tiers. Updates from
  `/dungeon_info` and dungeon announcements in chat. Individual tiers can be hidden.
- **Currency counter** - Totals the Black Gems, Forged Tungsten, Zircon, and Bloodstones in your inventory, shown as
  whole Black Gems plus change in Bloodstones. It can also include your ender chest, as of the last time you opened it.
- **Currency tally** - Tracks the currency you earn over the last hour, 3 hours, 12 hours, 24 hours, and week. Currency
  counts once when it first reaches you, including loot from dungeon and camp chests. Spending it doesn't lower the
  tally, and it doesn't count again when you take it back out of a chest, ender chest, shulker box or bundle you put it
  in, or pick up your own drop.
- **Mithril counter and tally** - Separate panels for your Mithril (Crude Mithril Ore) and the Mithril you earn, kept
  the same way as the currency counter and tally.
- **Arda Encyclopedia** - A searchable catalog of item prices, including upgrade costs.
- **Item tooltips and inventory worth** - Shows the price of known items in their tooltip and totals the value of your
  inventory.
- **Encyclopedia drafts** - Add your own local price entries from a held item.

The panels start in the top-left corner. **HUD Settings** (the HUD slot on the quick wheel) moves and scales
them, and sets their accent color and background transparency.

With Meteor Client installed, Arda Client adds:

- An **Arda Client** module category in Meteor's click GUI, with modules to switch the dungeon panel, currency counter,
  currency tally, Mithril counter, Mithril tally, profit estimates, and price tooltips on and off. They stay in sync with HUD Settings.
- **Arda Client** elements in Meteor's HUD editor for the dungeon panel, currency counter and tally, and Mithril counter and tally. While one is added
  there, Arda Client's own copy of it is hidden.

## Requirements

- Minecraft 26.2
- [Fabric Loader](https://fabricmc.net/use/) 0.19.3 or newer
- [Fabric API](https://modrinth.com/mod/fabric-api) 0.158.0 or newer
- Java 25
- Optional: [Meteor Client](https://meteorclient.com/) for 26.2
- Optional: [Xaero's Minimap](https://modrinth.com/mod/xaeros-minimap) (or Better PVP) and
  [Xaero's World Map](https://modrinth.com/mod/xaeros-world-map) for skirmish waypoints

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
- `arda-client/currency.json` - Your currency tally and last-seen ender chest total, kept for a week.

## Building from source

```bash
git clone https://github.com/GnJake/Arda-Client
cd Arda-Client
./gradlew build
```

The JAR is written to `build/libs`.

## License

Arda Client is available under the CC0 license.

## Developer builds

`./gradlew build -Pdev` builds `arda-client-<version>-dev.jar`, which adds the structure recorder (Camp Log, Tunnel Log
and Tower Log in the Structures screen) used to make the built-in list. Copy a recorded
`config/arda-client/structures.json` into `src/main/resources/arda-client/default_structures.json` to update it.
Developer builds are never released.
