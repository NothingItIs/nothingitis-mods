<div align="center">

<a href="https://modrinth.com/project/village-doctor"><img alt="Available on Modrinth" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/modrinth_vector.svg"></a>
<a href="https://www.curseforge.com/minecraft/mc-mods/village-doctor"><img alt="Available on CurseForge" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/curseforge_vector.svg"></a>
<a href="https://github.com/NothingItIs/nothingitis-mods/tree/main/villagedoctor"><img alt="Source on GitHub" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/github_vector.svg"></a>
<a href="https://discord.gg/zzXB5NeYzB"><img alt="Join our Discord" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/social/discord-plural_vector.svg"></a>

</div>

# Village Doctor

**Right-click a villager with a Stethoscope for a full checkup — and a plain diagnosis of why it won't breed, restock, or claim a bed or workstation.**

Villagers fail silently: no bed claimed, workstation stolen, not enough food, wrong time of day, item pickup switched off by a stray command. The game never tells you which one it is — Village Doctor does.

## What it does

- **Checkup** — right-click a villager with the Stethoscope for a live-updating screen: profession and level, bed and workstation with distances, trades with a countdown until locked trades refresh, breeding readiness with every blocking reason spelled out, your reputation and its price effect, and warnings only when something is actually wrong.
- **Outlines** — shift-right-click a villager to outline it and its bed and workstation in one color, visible through walls and only to you. Each villager gets a clearly different color, and outlining a whole village turns its bell black.
- **Owner lookup** — shift-right-click a bed, workstation, or bell to see which villager(s) own it; the owner flashes for a few seconds so you can find them.

## The Stethoscope

Craft it shapeless: **spyglass + string + copper ingot**. `/villagedoctor stethoscope` for ops only — configurable.

Optional `config/villagedoctor.properties` — permission level, outline limits and expiry, recipe and command toggles. Zero config needed by default.

## Modded villagers

Everything works on any villager built on Minecraft's standard villager type — vanilla-style villager mods included. Custom NPC or trader mods with their own entity classes aren't covered, and wandering traders aren't villagers, so they aren't either.

## Server support

Village Doctor is **server-side** — install it on the server and completely vanilla clients get everything, including the checkup screen. Works in singleplayer too.

**Optional client module** — the same jar also works on your client. With it installed, outlines upgrade to crisp wireframes that trace each block's real shape. Clients and servers mix freely across loaders — a NeoForge client on a Fabric server gets the upgrades. Vanilla clients lose nothing.

**Quilt** runs the Fabric jar, but needs **Quilt Loader 0.30.0 or newer**. Quilt may offer an older loader for 1.21.11; that one fails to start with a missing-`fabricloader` error.
