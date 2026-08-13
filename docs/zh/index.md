---
layout: home

hero:
  name: FakeModBlocker
  text: 轻量模组检测插件
  tagline: 通过插件频道与可选告示牌翻译检测，识别 Fabric / Forge 及常见实用模组。
  image:
    src: https://files.catbox.moe/8j2rr5.png
    alt: FakeModBlocker
  actions:
    - theme: brand
      text: 快速开始
      link: /zh/guide/introduction
    - theme: alt
      text: 查看 GitHub
      link: https://github.com/LostUmbrella58/FakeModBlocker

features:
  - title: 零依赖即用
    details: 放入 plugins/ 即可运行，无需硬依赖。支持 Spigot / Paper / Purpur / Folia 等。
  - title: 频道关键词匹配
    details: 按客户端注册的 Plugin Message Channel 识别加载器与模组，可自由扩展 forbiddenList。
  - title: 可选增强检测
    details: Paper 1.21.5+ 可用告示牌翻译键检测；安装 PacketEvents 可更早拦截频道注册。
  - title: 可选阶梯处罚
    details: 按玩家累计违规次数，逐级执行警告 → 踢出 → 封禁；记录持久化、可过期、完全可配。
  - title: 消息完全可配
    details: 自带中英双语语言文件、Hex 颜色、自定义踢出命令与员工通知。
---
