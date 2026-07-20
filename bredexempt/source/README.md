# BredExempt


**Your breeding and egg farms are quietly starving your natural spawns. BredExempt stops that.**


In vanilla Minecraft, every animal you **breed** — and every chick you **hatch from a thrown egg** — counts toward the **passive mob cap**. Pack a pen with cows, sheep, pigs, or chickens and you push the loaded area toward its cap: fewer animals spawn in the wild around you, and your farm slowly chokes natural spawning.


## What it does


- When two animals **breed**, it marks the **newborn and both parents** as persistent.
- When a chick **hatches from a thrown egg**, it marks the **chick** as persistent too.


Persistent mobs are skipped by the natural-spawn cap count — so your breeding *and* egg farms can grow as large as you like without suppressing spawns. Zero config, no performance cost.


Relies on vanilla's persistent-mob cap exemption, which holds on **vanilla, Fabric, and NeoForge**.  **Paper** servers have a long-standing bug where persistent mobs still count toward the cap, so the effect won't hold there.
