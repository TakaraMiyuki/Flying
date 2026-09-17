# Modrinth 发布清单（flying_enchant 1.0.0）

> 网页操作步骤。资料文件都在本目录：`description.md`（正文）、`summary.txt`（简述）、`changelog.md`（版本说明）、`icon.png`（512×512 项目图标）。

## 1. 注册账号

1. 打开 https://modrinth.com → 右上角 **Sign up**
2. 用 **Continue with GitHub**（OAuth，直接用你的 TakaraMiyuki 账号登录）

## 2. 创建项目

右上角 **"+" → Create a project** → 类型选 **Mod**，逐字段填写：

| 字段 | 填写内容 |
|---|---|
| Project name | `Flying Enchant`（不要加 "mod" 后缀） |
| Project slug | `flying-enchant` |
| Project icon | 上传本目录 `icon.png` |
| Project summary | `summary.txt` 内容（一句话英文简述） |
| Categories（Mod 分类） | 勾 **magic**（主）+ **game-mechanics** |
| Environments | 两个都勾（Client side / Server side —— 逻辑在服务端执行，客户端仅渲染粒子音效） |
| License | 选 **MIT**；License URL 可填 `https://github.com/TakaraMiyuki/Flying/blob/main/LICENSE` |
| External links | Source page: `https://github.com/TakaraMiyuki/Flying`；Report issues: `https://github.com/TakaraMiyuki/Flying/issues` |
| Discord | 留空 |
| Gallery | 可以后续再加游戏截图 |

## 3. 填写正文

- 打开项目 **Description** 编辑页，把 `description.md` 的**全部内容**复制粘贴进去（已是 Markdown 格式，直接可渲染）
- 保存后预览检查中英双语排版

## 4. 上传版本

项目 Dashboard → **Add a version**：

| 字段 | 填写内容 |
|---|---|
| Version number | `1.0.0` |
| Version title | `Flying Enchant 1.0.0` |
| Version type | **Release** |
| Files | 上传 `dist/flying_enchant-1.0.0.jar`（仓库根目录构建产物） |
| Game versions | 勾 **26.2** |
| Loaders | 勾 **neoforge** |
| Dependencies | **留空**（本模组无第三方依赖） |
| Changelog | 粘贴 `changelog.md` 内容 |

## 5. 提交审核

- 保存后项目状态变为 **Under review**
- 预期 24–48 小时，实际可能 2–4 周；审核期间可继续改页面/传新版本，不影响排队
- **不要催审、不要重复提交**

## 备注

- jar 内 modid 是 `flying_enchant`、版本 1.0.0、license MIT——与页面元数据一致（已核对）
- GitHub Release（v1.0.0）与 Modrinth 用同一构建，无需重发
- 以后自动发版可加 Minotaur Gradle 插件（需要 Modrinth PAT，Settings > Personal access tokens，CREATE_VERSION 权限）——需要时告诉我配置