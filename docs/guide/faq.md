# FAQ

## Can this detect hacked clients like Vape or LiquidBounce?

Usually no. Those clients often avoid detectable plugin message channels. This plugin is best for **mod loaders** such as Fabric, Forge, Lunar, and utility mods where you can configure a channel or translation key.

## Is this an anti-cheat?

No. It is a **mod presence detector**, not a cheat-behavior detection system.

## How does it work?

Primary path: check Plugin Message Channels on join (or via PacketEvents) against `forbiddenList`.

Optional path: on supported APIs, use virtual sign translation keys to detect certain mods.

## Does sign detection work on every version?

No. It needs Paper's public virtual sign API (~1.21.5+). Unsupported servers skip the module automatically; the main feature still works.

## I added a mod name but nothing happens. Why?

`forbiddenList` matches **channel keywords**, not display names. Enable `logger`, copy the real channel, then add it. If the mod never registers a channel, use sign translation detection instead.

## Staff got kicked by mistake. What now?

Give them `fakemodblocker.bypass`, or temporarily set `enable: false` and `/modblocker reload`.
