# Flying (飞翔) — Enchantment Mod for NeoForge 26.2

**Flying** is an enchantment for the **Mace** that grants an air dash: after a wind-burst smash bounce, press the jump key mid-air to dash forward-upward.

## What it does

- New enchantment **Flying** (Lv I–II), applied to the Mace **at an anvil** with an enchanted book (the enchanting table never rolls it)
- **Prerequisite:** the mace must already have **Wind Burst** — applying a Flying book to a mace without Wind Burst is rejected
- **Lv I:** dash ~3 blocks · **Lv II:** dash ~4 blocks, rising ~0.5 / 1 block respectively
- **Costs per dash:** 1 weapon durability + 2 hunger (immune in creative); below the sprint food threshold (≤ 6) dashing is unavailable
- **Once per launch:** the dash refreshes on landing, or on the next wind-burst bounce when chaining smashes
- Effects: a small ring of white wind particles at your feet and the wind-burst sound

## Why download it

Wind Burst already rewards smash attacks with a bounce — Flying turns that bounce into momentum. Chain smash → bounce → dash → smash across a battlefield, or use the dash as an escape tool.

## Notes before downloading

- Requires **Minecraft 26.2** with **NeoForge 26.2.0.88** (compatible with 26.2.0.82+), **Java 25**
- Works in singleplayer and on servers; on servers the enchantment must be present for players to use it
- **Server admins:** air dashing can be disabled server-wide with `/gamerule flyingDash false` (default `true`)
- Optional companion mod **Xingyue** (source in the same [repository](https://github.com/TakaraMiyuki/Flying), jar not published yet): with both installed, the Xingyue sword can also take Flying — the two mods share an item tag, so any combination of the two mods works

## 获取方式 / How to obtain

1. Craft/obtain an anvil
2. Enchant the mace with **Wind Burst** (e.g. via anvil + enchanted book)
3. Apply a **Flying** enchanted book to the mace at the anvil

---

## 中文说明

**飞翔**是重锤专属的机动性附魔：下坠攻击触发风爆弹起后，按跳跃键即可在空中向前上方突进。

- 新附魔**飞翔**（1–2 级），仅能通过**铁砧 + 附魔书**应用（附魔台不出此附魔）
- **前提**：重锤必须已附魔**风爆**——未附风爆的重锤会被直接拒绝
- Ⅰ 级突进约 3 格 / Ⅱ 级约 4 格，抬高约 0.5 / 1 格
- **每次突进消耗**：1 点武器耐久 + 2 点饥饿（创造模式豁免）；饱食度不高于疾跑阈值（6 点）时不可用
- **每次跃起限一次**：落地或下一次风爆弹跳后刷新资格
- 特效：脚下泛起白色风爆粒子并伴随风爆音效

**服务器管理员**：`/gamerule flyingDash false` 可全服禁用空中突进（默认开启）。

可选联动：同时安装星月模组（本仓库 `Xingyue/` 子项目，暂不发布 jar）时，星月剑也可获得飞翔附魔——两个模组通过共享物品标签联动，任意安装组合均正常工作。

## License

[MIT](https://github.com/TakaraMiyuki/Flying/blob/main/LICENSE)