# Introduction

FakeModBlocker is a **lightweight, configurable** Minecraft server plugin for detecting some client mods / mod loaders.

It primarily inspects **Plugin Message Channels** exposed when a player joins. On supported server APIs, it can also enable optional **sign translation key detection**.

::: warning Important
FakeModBlocker is **not** a full anti-cheat, and it **cannot** detect every cheat client or mod.

Many client-only mods (some HUDs, movement cheats, etc.) **never** register a plugin channel, so the channel list cannot catch them. Treat this as a **mod presence detector**, not a universal blocker.
:::

## Features

- Lightweight, no hard dependencies
- Customize messages and behavior via `messages_xx.yml`
- Hex color support on 1.16+ (e.g. `&#00ffcc`)
- Main detection aimed at roughly **1.8–1.21.11+**
- Bilingual by default: `en` and `cn` both ship, or add your own files
- Hot reload with `/modblocker reload`
- Spigot / Paper / Purpur / Folia / PandaSpigot
- Optional: sign translation detection, PacketEvents enhancement

## What it can detect

| Type | Examples |
|------|----------|
| Loader channels | Forge (`fml:hs`), Fabric (`fabric:registry/sync`) |
| Map / utility mod channels | Xaero, VoxelMap, Replay, Freecam, etc. (if they register channels) |
| Custom keywords | Anything you add to `forbiddenList` |
| Translation keys (optional) | Mods configured under `extra-detections.sign-translation` |

## Two detection modes

1. **Plugin channel detection (primary)** — widest compatibility across versions.
2. **Sign translation detection (optional)** — needs a public server API (Paper `openVirtualSign`, ~1.21.5+). If unsupported, it is skipped automatically without breaking the main plugin.

Next: [Installation](./installation)
