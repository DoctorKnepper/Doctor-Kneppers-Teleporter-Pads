# Teleporter Pads

Futuristic teleporter pads in all 16 dye colors. Stand on a pad and it sends you to your other pad of the
same color: across the world, or into another dimension.

![Teleporter Pads](src/main/resources/assets/teleporterpads/icon.png)

## How it works

- **Craft a pair** (the recipe makes two White Teleporter Pads) and place them anywhere.
- **Stand on one** for a moment. A swirl of the pad's color rises around you, and then you're on the other pad.
- **Color is the channel.** Pads only link with pads of the **same color** that **you** placed. With 16 colors
  you can run 16 separate routes: red to your mine, blue to your farm, purple to your Nether base, and so on.
- **More than two of one color?** They form a loop in the order you placed them. Each trip takes you to the
  next pad, and the message on screen tells you which one (for example "Red pad 2 of 3").
- **Change a pad's color** by using a dye on it, placed or not. You can also craft any pad with a dye.
- **Distance and dimensions don't matter.** The destination is loaded for you, even in the Nether or the End.
- After arriving, step off the pad before it will send you on again, so you never bounce straight back.
- **Sneak** on a pad to stand on it without teleporting.
- A pad whose two blocks above are blocked is skipped. A pad that was broken is forgotten.
- Pads glow a little in the dark, and pistons can't move them.

Other players can use your pads too. They go to the next pad of that color in your network.

## Recipes

Teleporter Pad (makes 2 white pads):

| | | |
|:-:|:-:|:-:|
|   | Ender Pearl |   |
| Iron Ingot | Redstone | Iron Ingot |
| Smooth Stone Slab | Smooth Stone Slab | Smooth Stone Slab |

Recoloring: any Teleporter Pad plus any dye.

The pad recipe unlocks when you pick up an ender pearl. The dye recipes unlock when you have a pad.

## Requirements

- Minecraft **26.3** (Java Edition)
- [Fabric Loader](https://fabricmc.net/use/) 0.20.1 or newer
- [Fabric API](https://modrinth.com/mod/fabric-api)

Install it on both the client and the server.

## Installing

Download `teleporter-pads-<version>.jar` from the [releases page](https://github.com/DoctorKnepper/MC-Teleporter-Pads/releases), Modrinth or CurseForge, and put it in your `mods` folder along with Fabric API.

## Building from source

You need a Java 25 JDK.

```sh
./gradlew build    # the mod jar ends up in build/libs/
```

## Releasing

Publishing a GitHub release (Releases, then "Draft a new release", with a tag like `v1.0.0`) runs
`.github/workflows/release.yml`. It builds the jar, attaches it to the release and uploads it to Modrinth
and CurseForge. It needs these repository settings (Settings, then "Secrets and variables", then "Actions"):

| Name | Kind | Value |
|---|---|---|
| `MODRINTH_TOKEN` | secret | a Modrinth personal access token with the "Create versions" scope |
| `CURSEFORGE_TOKEN` | secret | a CurseForge API token (from your CurseForge account's API tokens page) |
| `MODRINTH_ID` | variable | the Modrinth project ID |
| `CURSEFORGE_ID` | variable | the CurseForge project ID |

If one platform's values aren't set, the workflow skips that platform. Update `mod_version` in
`gradle.properties` before tagging a new release.

## License

CC0 1.0 (public domain). See [LICENSE](LICENSE).
