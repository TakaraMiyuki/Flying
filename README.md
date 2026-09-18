# 飞翔 (Flying)

**Minecraft Java 26.2 + NeoForge 26.2.0.88** 的原创附魔模组。

**"飞翔"是专为重锤设计的机动性附魔：下坠攻击触发风爆弹起后，按跳跃键即可在空中向前上方突进。** 1.2.0 起另提供同名的**状态效果**版本，疾跑中按跳跃键即可突进。

## 附魔版

- **附魔对象**：仅重锤；安装[星月模组](https://github.com/TakaraMiyuki/Xingyue)后也可作用于星月
- **获取前提**：重锤必须**先附魔风爆 (Wind Burst)**，才能在铁砧应用飞翔附魔书（未附风爆时会被直接拒绝）
- **等级**：Ⅰ 级突进约 3 格 / Ⅱ 级约 4 格，抬高约 0.5 / 1 格
- **消耗**：每次突进损耗 1 点武器耐久、2 点饥饿（不受等级影响，创造模式豁免）
- **限制**：饱食度不高于疾跑阈值（6 点）时无法突进；每次跃起只能突进一次，落地或下一次弹跳后刷新
- **特效**：突进时脚下泛起白色风爆粒子并伴随风爆音效

## 状态效果版（1.2.0 新增）

- **效果 id**：`flying_enchant:flying`（中文名"飞翔"），通过命令 / 数据包 / 其他模组施加，例如：`/effect give @s flying_enchant:flying 30`
- **触发**：持有效果期间，**疾跑状态下按跳跃键**即向前上方突进（水平约 2 格、抬高约 2 格），突进时整体重置下坠速度；风爆弹跳后可**在空中按住疾跑键+前进键起跑**再按跳跃接续突进（每 10 刻冷却）
- **消耗**：每次 1 点饥饿（不绑定武器、无耐久消耗，创造豁免）；每 10 游戏刻冷却
- **限制**：同样受饱食度门槛（> 6）与 gamerule 管辖；**除玩家以外的所有生物免疫该效果**
- 与附魔版**并存互不影响**，两者可各用各的

## 服务端开关

服务器管理员可用 `/gamerule flying_enchant:flying_dash false` 全服禁用突进（附魔版与效果版同时受管辖；默认开启，`true` 恢复）。

## 安装

1. 安装 [NeoForge 26.2.0.88](https://neoforged.net/)（兼容 26.2.0.82+）
2. 从 [Releases](https://github.com/TakaraMiyuki/Flying/releases) 下载 `flying_enchant-1.2.0.jar` 放入 `mods/`
3. 铁砧合成"飞翔"附魔书（附魔台不出此附魔），先给重锤附风爆，再附飞翔；或用命令给自己飞翔效果

## 联动模组：星月 (Xingyue)

[星月](https://github.com/TakaraMiyuki/Xingyue)是一把蓄力爆发之剑（测试版）。两个模组**互相独立、互为可选依赖**——通过共享物品标签 `#xingyue:enchantable/flying` 联动，任意安装组合均正确工作。

> 1.1.0 起飞翔的附魔 id 迁移至 `xingyue:flying`（此前 1.0.0 使用 `examplemod:flying`，升级后旧附魔需重新获取）。状态效果为本模组自有内容，id 是 `flying_enchant:flying`。

## 构建

```bash
./gradlew build    # 产物在 build/libs/
```

要求 JDK 25。

## License

[MIT](LICENSE)
