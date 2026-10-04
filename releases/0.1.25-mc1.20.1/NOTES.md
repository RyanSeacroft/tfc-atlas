TFC Atlas 0.1.25 for Minecraft 1.20.1 (Forge).

- View TFC layers everywhere, only over explored terrain, or only over unexplored terrain. Unexplored only is still the default.
- Switch between TFC layers, Xaero surface maps and Xaero cave maps from the map toolbar. The original map data stays untouched.
- Map labels follow the selected coverage mode.
- Backported the autocomplete overflow fix: typed text and the suggestion shift left together before completion is accepted.

Built against TFC 3.2.18 and Xaero's World Map 1.39.12. Replace the old Atlas JAR in your mods folder with `tfc-atlas-1.20.1-0.1.25.jar`.

Compilation, formatting, lint and automated regression tests passed. This build still needs an in-game visual check. Cave view requires Xaero cave maps to be available.
