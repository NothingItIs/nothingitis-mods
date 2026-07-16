<div align="center">

<a href="https://modrinth.com/mod/bredexempt"><img alt="Available on Modrinth" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/modrinth_vector.svg"></a>
<a href="https://www.curseforge.com/minecraft/mc-mods/bredexempt"><img alt="Available on CurseForge" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/curseforge_vector.svg"></a>
<a href="https://github.com/NothingItIs/nothingitis-mods/tree/main/bredexempt"><img alt="Source on GitHub" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/github_vector.svg"></a>
<a href="https://discord.gg/zzXB5NeYzB"><img alt="Join our Discord" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/social/discord-plural_vector.svg"></a>

</div>

# BredExempt

**Your breeding and egg farms are quietly starving your natural spawns. BredExempt stops that.**

In vanilla Minecraft, every animal you **breed** — and every chick you **hatch from a thrown egg** — counts toward the **passive mob cap**. Pack a pen with cows, sheep, pigs, or chickens and you push the loaded area toward its cap: fewer animals spawn in the wild around you, and your farm slowly chokes natural spawning.

## What it does

- When two animals **breed**, it marks the **newborn and both parents** as persistent.
- When a chick **hatches from a thrown egg**, it marks the **chick** as persistent too.

Persistent mobs are skipped by the natural-spawn cap count — so your breeding *and* egg farms stop consuming the passive-creature cap, leaving that room for naturally spawning wildlife.

## Commands & config

- `/bredexempt inspect` — look at an animal: marked or not, why, and whether it still counts toward the cap. Click the output to copy it.
- `/bredexempt cap` — live cap accounting for your dimension: counted vs cap, exempt vs wild.
- Optional `config/bredexempt.properties` — toggles for offspring / parents / egg-hatched chicks, plus an entity allowlist/denylist. Zero config needed by default.
- Modpacks can use the `#bredexempt:allowed` / `#bredexempt:denied` entity tags instead — a plain datapack, no config edits.

## Modded animals

Modded animals are marked exactly like vanilla ones **when their mod uses Minecraft's standard `Animal` breeding flow** — many vanilla-style farm/wildlife mods do. Modded wildlife gains spawn room when it spawns through the vanilla natural-spawning system in the standard creature category.

Mods with **custom breeding systems, custom spawners, custom mob caps, or custom egg/hatching mechanics** may not be covered — if you find one, open an issue and dedicated support can be looked at.

## Server support

Relies on vanilla's persistent-mob cap exemption.
