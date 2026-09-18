## Flying (飞翔) 1.2.0 — Flying Mob Effect

- New **mob effect** `flying_enchant:flying` (Lv I-II) alongside the enchantment (both coexist)
  - Lv I: dash ~2 blocks forward / ~2 up - **Lv II: ~5 forward / ~3 up** (`/effect give @s flying_enchant:flying 30 1`)
  - Trigger: **press jump while sprinting**; unlimited mid-air dashes while sprinting (10-tick cooldown)
  - Fall speed is reset on dash, same as the enchantment; costs **0.5 hunger** per use (half-point accumulator, creative immune)
  - Obtained via `/effect give` / datapacks / other mods; icon: a custom flight swoosh
  - All non-player entities are **immune** to the effect
  - **Dual priority:** with both the enchantment and the effect, a dash spends the enchantment's once-per-launch first, then falls through to the effect; a wind-burst relaunch re-prioritizes the enchantment
- Sprint state now travels with the dash request, so the desync-prone server-side sprint mirror is no longer used for gating
- **Fix:** the gamerule is renamed `flyingDash` → **`flying_enchant:flying_dash`** — in 26.2 gamerule ids are registry identifiers (lowercase, namespaced) and the rule must be registered during the registry phase; the old camelCase id crashed the mod during construction (a real crash present since 1.0.0)

---

## 飞翔 (飞翔) 1.2.0 — 飞翔状态效果

- 附魔之外新增**状态效果** `flying_enchant:flying`（1-2 级），两种玩法并存互不影响
  - 一级水平约 2 格/上 2 格；**二级水平约 5 格/上 3 格**（`/effect give @s flying_enchant:flying 30 1`）
  - 触发：**疾跑状态下按跳跃键**；疾跑中空中**不限次数**连发（每 10 刻冷却）
  - 发动时整体重置下坠速度（继承附魔版特性）；每次消耗 **0.5 点饥饿**（半点累积，两次扣 1 点，创造豁免）
  - 通过 `/effect give`、数据包或其他模组施加；图标为定制飞翔轨迹
  - **除玩家以外的所有生物免疫**该效果
  - **双持优先级**：同持附魔与效果时优先消耗附魔的一次跃起，之后落到效果连发；风爆跃起重置附魔状态后附魔重新优先
- 突进请求重构为可仲裁的单包：疾跑标志随包上送，不再依赖会脱同步的服务端疾跑镜像
- **修复**：gamerule 更名 `flyingDash` → **`flying_enchant:flying_dash`**——26.2 中 gamerule 是注册表驱动的 Identifier（必须全小写）且必须在注册阶段注册，旧的驼峰命名会在注册时抛异常使模组构造直接崩溃（1.0.0 起即存在的真实崩溃）

---

## Flying (飞翔) 1.0.0 — Initial Release

- New enchantment **Flying** for the Mace (Lv I–II): air dash forward-upward after a wind-burst bounce
- Requires **Wind Burst** on the mace before Flying can be applied at an anvil
- Lv I: ~3 blocks, Lv II: ~4 blocks; rise ~0.5 / 1 block
- Costs: 1 durability + 2 hunger per dash (creative immune); below the sprint food threshold (6) dashing is disabled
- One dash per launch; eligibility refreshes on landing or on the next wind-burst bounce
- Server admins can disable dashing server-wide with `/gamerule flying_enchant:flying_dash false`

---

## 飞翔 (飞翔) 1.0.0 — 首次发布

- 重锤新增**飞翔**附魔（1–2 级）：风爆下坠弹起后可空中向前上方突进
- 铁砧应用前提：重锤需先附魔**风爆**
- Ⅰ 级约 3 格 / Ⅱ 级约 4 格，抬高约 0.5 / 1 格
- 每次突进消耗 1 点耐久 + 2 点饥饿（创造豁免）；饱食度 ≤ 6 时不可用
- 每次跃起限一次，落地或下一次风爆弹跳刷新
- 服务器管理员可用 `/gamerule flying_enchant:flying_dash false` 全服禁用
