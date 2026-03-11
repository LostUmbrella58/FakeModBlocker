# 🧩 FakeModBlocker

A **lightweight**, **fully customizable**, and **cross-version friendly** mod detection plugin that monitors plugin channels to identify some mods like Xaero's World Map, Replay Mod, and known mod loaders such as **Fabric**, **Forge**, and more.

It also supports an **optional advanced sign translation detection module** on supported server implementations.

---

<img src="https://files.catbox.moe/8j2rr5.png" width="384" height="206" alt=""/>

## ✨ Features

✅ Lightweight — zero unnecessary overhead  
✅ Fully customizable messages and behavior via `messages_xx.yml`  
✅ Hex Color Code support for 1.16+ servers (e.g. `&#00ffcc`)  
✅ No dependencies — drop-in and go  
✅ Works across a wide range of server versions (1.8–1.21.11+)  
✅ Multi-language support (`en`, `cn`, and your own language)  
✅ Reload support: `/modblocker reload`  
✅ Spigot / Paper / Purpur / Folia / PandaSpigot support  
✅ Optional advanced **sign translation key detection** on supported server APIs

---

## 🔍 What it Detects

FakeModBlocker primarily detects **clients or mods that register known plugin message channels**, such as:

- `fml:hs` → Forge
- `fabric:registry/sync` → Fabric
- `xaeroworldmap` → Xaero's World Map
- Any custom channel you define

In addition, FakeModBlocker also provides an **optional sign translation detection module** for certain mods that expose identifiable translation keys.  
This advanced detection is configurable and can apply actions such as:

- `NOTICE`
- `KICK`
- `BAN`
- `IGNORE`

---

## 🧠 Detection Modes

### 1. Plugin Channel Detection
This is the main detection method and works across the widest range of versions.

It checks which `Plugin Message Channels` the player is listening on when they join.  
Many mod clients (e.g. Fabric / Forge and some utility mods) automatically register custom channels, which this plugin can detect and respond to.

### 2. Optional Sign Translation Detection
This is an **extra optional detection module**.

It works by opening a sign editor with specific translation keys and checking whether the client translates them, which may reveal the presence of certain mods.

⚠️ This feature depends on **server API support for opening sign editors**.  
If the current server version / implementation does not support the required public API, FakeModBlocker will **automatically disable this feature and skip it safely**.

---

## ⚠️ Important Notes

⚠️ **FakeModBlocker does NOT detect all mods or cheating tools.**  
It only works when the client exposes something detectable, such as known plugin message channels or supported translation-key behavior.

⚠️ This plugin should **NOT** be treated as a full anti-cheat or universal mod detector.

⚠️ The optional sign translation detection is an **additional enhancement**, not the main detection system.

This makes FakeModBlocker ideal for detecting casual use of mod loaders or certain utility mods, but it should **not be treated as a complete anti-cheat / mod-blocking solution**.

---

## 📂 Installation

1. Drop the `.jar` into `plugins/`
2. Start the server to generate config files
3. Edit `config.yml` and your language file
4. Use `/modblocker reload` to apply changes instantly
5. Some mods are pre-configured in `config.yml`, and you can freely modify them to match your server rules

---

## ⚙️ Compatibility

- Main plugin channel detection is designed to be broadly compatible with **1.8–1.21.11+**
- Optional sign translation detection requires a compatible server API
- On unsupported versions / server implementations, the sign detection module will be **disabled automatically**
- Folia is supported through compatible scheduler handling

---

## ❓ FAQ

### Q: Can this detect hacked clients like Vape or LiquidBounce?
**A:** No. Those clients often avoid using detectable plugin message channels. This plugin is best used to detect **mod loaders** like Fabric, Forge, Lunar, etc., and some configurable translation-key based mod detections on supported servers.

### Q: Is this an anti-cheat?
**A:** No. This is a **mod presence detector**, not a cheat detection system.

### Q: How does it work?
**A:** Its main method is checking which `Plugin Message Channels` the player is listening on when they join. Many mod clients automatically register custom channels, which this plugin can detect.  
Additionally, on supported server APIs, it can optionally use **sign translation key detection** for certain configurable mod signatures.

### Q: Does the sign detection work on every version?
**A:** No. The sign detection module depends on public sign editor API support (Sorry i don't have much time to test every single version, but it should work on every Paper server i guess). If the server does not support it, FakeModBlocker will automatically skip that module without breaking the plugin.