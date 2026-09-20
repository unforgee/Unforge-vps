# Unforge admin tools

## Plugins

- **Unforge Teleporter**: client-side panel with grouped destinations for cities, guilds, skilling, monsters, bosses, minigames and Wilderness.
- **Developer Hub lists**: select a category on the left and browse its contents in the central scrollable list. Item/equipment/gear/misc stock is split into 248-item pages when needed, with scrolling inside each page; item rows keep the existing Take-1/5/50/X and Examine actions. Teleports include spellbooks, cities, bosses, monsters, minigames, raids, dungeons, Wilderness and other destinations, using the server's Eco/PvP/Test catalog without item-style pagination.
- **Unforge NPC Area Builder**: client-side panel for placing an NPC at an exact X/Y/plane coordinate. The current in-game position can be copied into the form.

Both plugins send the existing admin cheat-command channel. They are therefore only usable by a player with the server's admin command level.

## Server commands

The existing commands remain available:

```text
::tele level mapX mapZ localX localZ
::npcadd duration npcDebugNameOrId
```

The NPC Area Builder uses the new exact-coordinate command:

```text
::npcaddat duration npcDebugNameOrId level mapX mapZ localX localZ
```

The client converts the familiar absolute X/Y/plane position into the server's `CoordGrid` fields before sending the command.

## Teleport source

The destination coordinates were taken from the existing Kronos list and adapted to the current client command format:

```text
Kronos-184/Kronos-master/kronos-server/data/teleports_eco.json
```

The Kronos folder itself was not modified.
