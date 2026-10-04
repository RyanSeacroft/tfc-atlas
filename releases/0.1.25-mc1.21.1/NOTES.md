TFC Atlas 0.1.25 for Minecraft 1.21.1 (NeoForge).

- View TFC layers everywhere, only over explored terrain, or only over unexplored terrain. Unexplored only is still the default.
- Switch between TFC layers, Xaero surface maps and Xaero cave maps from the map toolbar. The original map data stays untouched.
- Map labels follow the selected coverage mode.
- New approximate soil-region layer, with readable names and nutrient bonuses in the key and cursor information.
- Existing terrain caches are reused and upgraded with soil data as needed.

Built against TFC 4.2.11 and Xaero's World Map 1.46.0. Replace the old Atlas JAR in your mods folder with `tfc-atlas-neoforge-1.21.1-0.1.25.jar`.

Soil regions are a scouting aid. Local elevation, forest decoration, patches and custom world generation can change the soil at a particular block. Bonuses describe nutrient gain, not unfertilised crop growth speed.

Compilation, formatting, lint and automated regression tests passed. This build still needs an in-game visual check. Cave view requires Xaero cave maps to be available.
