Now runs on Minecraft 1.21.11 as well as 26.x, and fixes an outline bug that affected everyone.

- Added support for Minecraft 1.21.11 on both Fabric and NeoForge
- Fixed: shift-right-clicking a villager could switch the outline on and straight back off, leaving it looking like the gesture did nothing. Vanilla sends two interaction packets for a single click, so the toggle ran twice. Affected 26.x too — most visibly for players on a vanilla client
- Fixed: the trade-refresh countdown in the checkup could read the wrong time of day
- Existing 26.1–26.2 support is unchanged
- Downloads are split by Minecraft version — pick the file whose name matches the version you play
