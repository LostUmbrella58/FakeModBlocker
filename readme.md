# 🧩 FakeModBlocker

A **lightweight**, **fully customizable**, **version-independent** mod detection plugin that monitors plugin channels to identify some mods like Xaeroworldmap, Replay ... and known mod loaders such as **Fabric**, **Forge**, and more.

---

<img src="https://files.catbox.moe/8j2rr5.png" width="384" height="206"  alt=""/>

## ✨ Features

✅ Lightweight — zero unnecessary overhead  
✅ Fully customizable messages and behavior via `messages_xx.yml`  
✅ Hex Color Code support for 1.16+ servers (e.g. `&#00ffcc`)  
✅ No dependencies — drop-in and go  
✅ Works with any server version (1.8–1.21+)  
✅ Multi-language (`en`, `cn`, `and your own language`)  
✅ Reload support: `/modblocker reload`

---

## 🔍 What it Detects

FakeModBlocker detects **clients or mods that register known plugin message channels**, such as:

- `fml:hs` → Forge
- `fabric:registry/sync` → Fabric
- `xaeroworldmap` → Xaeroworldmap mod
- Any custom channel you define

⚠️ Note:
> FakeModBlocker does **not** detect all mods or cheating tools. It only works if the client sends identifiable plugin messages.

---

## 📂 Installation

1. Drop the `.jar` into `plugins/`
2. Start server to generate config
3. Edit `config.yml` and language files
4. Use `/modblocker reload` to apply changes instantly
5. Some mods are pre-set in config.yml, you can modify on your own to fit your server's rule

---

## ❓ FAQ

### Q: Can this detect hack clients like LiquidBounce, Vape, and so on?
**A:** No. Those clients often avoid using detectable plugin message channels. This plugin is best used to detect **mod loaders** like Fabric, Forge, Lunar, etc.

### Q: Is this an anti-cheat?
**A:** No. This is a **mod presence detector**, not a cheat detection system.

### Q: How does it work?
**A:** It checks which `Plugin Message Channels` the player is listening on when they join. Many mod clients (e.g., Fabric/Forge) automatically register custom channels, which this plugin can detect and respond to.
