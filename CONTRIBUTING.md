# Working on Atlas

Use the branch for the Minecraft version you’re changing. The newest supported version is the default branch; the older ones stay separate.

## Build it

Install Java 21, then run these from the project folder. Gradle downloads the matching compiler if that branch needs another Java version.

```sh
./gradlew spotlessApply
./gradlew build
```

On Windows, use `gradlew.bat`. Your JAR is in `build/libs/`.

`spotlessApply` fixes formatting. `build` checks formatting and lint, runs the existing tests, and builds the mod. GitHub runs the same checks on pushes and pull requests. Lint reports are in `build/reports/checkstyle/`.

## Keep it readable

- Let Spotless handle spacing, imports and wrapping. We use four-space Java indentation.
- Use explicit imports so it’s clear which Minecraft or TFC class a name refers to.
- Use braces for `if`, `else` and loops, even for a single line.
- Give methods and variables names that explain their job. Short `x`/`z` names are fine for coordinates.
- Keep methods focused. Pull a separate step into a named method when a block gets hard to follow.
- Write comments for reasons and awkward Minecraft/TFC behaviour, rather than narrating each line.
- Keep predictions in `core/`, game and UI integration in `client/`, and hooks into other mods in `mixin/`.

Checkstyle catches things like empty statements, accidental switch fall-through, and multiple statements on a line. These checks are here to help the next person reading the code.

For changes to maps or UI, also try `./gradlew runClient` and check them in game. The regular tests don’t replace that.

## Fix a bug

1. Select the Minecraft branch affected by the bug in GitHub Desktop, then fetch and pull.
2. Make the fix on that branch, or create a short-lived branch for a larger change.
3. Run `./gradlew spotlessApply build` and try the affected behaviour in game.
4. Commit with a short description of the fix, then push. Check the GitHub build result.
5. If the bug affects another Minecraft version, apply the relevant fix on that version's branch and test it there too. Do not merge an entire Minecraft port into the older branch.
6. When ready to release, bump `mod_version` on each affected branch and follow the release steps below.

Keep the existing repository for future fixes. Git records the history, and selecting a branch changes the files in your working folder automatically.

## Publish the included releases

The repository includes checked JARs, source ZIPs, notes and checksums for both initial 0.1.24 releases. Publish both Minecraft branches, then run **Actions → Publish prepared releases** once from the default branch. It publishes both releases with the right source tags. Existing matching downloads are kept; changed files or conflicting tags stop the affected job.

That workflow uses the prepared files as they are. For later fixes, use the build-and-draft steps below after changing `mod_version`.

## Make a release

1. Change `mod_version` in `gradle.properties`. The JAR name and mod metadata use it automatically.
2. Run `./gradlew releaseZip`. This runs the checks and produces a source ZIP in `build/releases/`, with the built JAR inside it.
3. Push the commit, then open **Actions → Draft release → Run workflow** on the right branch. It builds again and creates a **draft** GitHub release with the JAR and source ZIP.
4. Add a short description of what changed, check the Minecraft version, then publish the draft.

The draft workflow uses a tag like `v<mod version>-mc<Minecraft version>`. Existing tags and releases are never overwritten.

## Support another Minecraft version

1. Make a branch from the closest working version. Follow TFC’s names, such as `1.21.x`.
2. Update the version and dependency entries in `gradle.properties`. Keep the metadata ranges as narrow as the versions you’ve actually tested.
3. Port the game-facing code in `client/` and `mixin/`. Check TFC’s world generation too: climate, rocks and sampling can change even when the code still compiles.
4. Run `./gradlew spotlessApply build releaseZip`, then test in game. Set the new default branch when it’s ready.

The build and draft-release workflows read their versions from the selected branch. The one-time prepared-release workflow lists the two included branches. `gradle/atlas.gradle` holds the shared formatting, metadata and packaging setup; `build.gradle` holds the loader-specific bits. Copy shared maintenance changes to the older branches without merging an entire port into them.

Updating version numbers starts a port; it can’t make incompatible Minecraft APIs work automatically.
