# NeoForge 26.2 开发环境说明

本工作区由官方 MDK 模板 [MDK-26.2-ModDevGradle](https://github.com/NeoForgeMDKs/MDK-26.2-ModDevGradle) 初始化，环境已验证可构建。

| 项目 | 版本 |
|---|---|
| Minecraft | **26.2**（2026 年新版号体系，正式版） |
| NeoForge | **26.2.0.82**（构建于 `gradle.properties` 的 `neo_version`） |
| 构建插件 | ModDevGradle 2.0.146（`build.gradle`） |
| Gradle | 9.2.1（项目自带 wrapper，无需全局安装） |
| Java | **25**（本机已装 Oracle JDK 25.0.1，`build.gradle` 工具链锁定 25） |

## 常用命令

在项目根目录（Git Bash 用 `./gradlew`，CMD/PowerShell 用 `gradlew.bat`）：

| 命令 | 作用 |
|---|---|
| `./gradlew build` | 编译并打包，产物在 `build/libs/examplemod-1.0.0.jar` |
| `./gradlew runClient` | 启动开发版 Minecraft 客户端（模组已加载） |
| `./gradlew runServer` | 启动开发版专用服务端（首次需在 `run/eula.txt` 中把 `eula` 改为 `true`） |
| `./gradlew runData` | 运行数据生成器，输出到 `src/generated/resources` |
| `./gradlew runGameTestServer` | 运行注册的 GameTest（默认无测试会报错，属正常） |
| `./gradlew clean` | 清理 `build/` |

> 开发客户端/服务端的游戏目录在项目下的 `run/`，日志在 `run/logs/`。
> `build.gradle` 中日志级别默认 DEBUG，控制台输出很多，属模板默认行为，可在 `neoForge.runs.configureEach` 中调整。

## 在 IntelliJ IDEA 中打开

1. **File → Open**，选择 `F:\ZcodeBuild\examplemod` 目录（选根目录，不要选 build.gradle）
2. 首次打开信任项目，等待右下角 Gradle 同步完成（首次同步会再下载部分依赖，耐心等待）
3. 同步完成后，运行配置下拉框会出现 `runClient` / `runServer` / `runData` 等，点击即可运行
4. 建议保持 IDEA 使用项目的 Gradle wrapper（默认即是），不要换成本地 Gradle

ModDevGradle 会在同步时自动生成运行配置与 IDE 元数据；如遇配置异常，可执行 `./gradlew build` 后重新同步。

## 后续改名清单（把 examplemod 换成你的模组）

改名的核心是保持三处一致：`gradle.properties` 的 `mod_id`、`ExampleMod.java` 的 `MODID` 常量（即 `@Mod` 注解值）、资源目录 `assets/examplemod/`。建议按顺序做：

1. **`gradle.properties`**：改 `mod_id`（小写，须匹配 `[a-z][a-z0-9_]{1,63}`）、`mod_name`、`mod_group_id`（同时对应 Java 包名基座）、`mod_version`
2. **包名**：在 IDEA 中对 `src/main/java/com/example/examplemod` 用 Refactor → Rename 改为新包（`mod_group_id` 应与包名一致）
3. **主类**：`ExampleMod.java` 中 `MODID` 常量改为新 `mod_id`；类名可一并重构（`@Mod(ExampleMod.MODID)` 与 `ExampleModClient` 中的引用会随之更新）；`neoforge.mods.toml` 中的 `modId` 是由 `${mod_id}` 占位符自动展开的，无需手改
4. **资源目录**：`src/main/resources/assets/examplemod/` 改为 `assets/<新mod_id>/`（lang、贴图、模型都放这下面）
5. **全局搜索** `examplemod` 检查残留（如 lang 文件的 key `itemGroup.examplemod` 等）
6. 跑一遍 `./gradlew build` 验证

## 升级 Minecraft / NeoForge 版本

到 https://projects.neoforged.net/neoforged/neoforge 查询某 MC 版本对应的最新 NeoForge 构建，然后改 `gradle.properties` 三行并重新构建：

```properties
minecraft_version=26.2          # 目标 MC 版本
minecraft_version_range=[26.2]  # 通常与上面同步
neo_version=26.2.0.82           # 该 MC 版本对应的 NeoForge 最新构建
```

> 版本号规则（26.x 起）：NeoForge 版本前三位对应 MC 版本及热修号，第四位是 NeoForge 自身构建序号，如 `26.2.0.82` = MC 26.2 的第 82 个构建。跨大版本升级（如 26.2 → 26.3）可能涉及 API 变动，参照 https://docs.neoforged.net 的更新说明。

## 依赖与网络

- 首次构建需访问：`services.gradle.org`、`maven.neoforged.net`、`piston-meta.mojang.com`、`libraries.minecraft.net`、`repo.maven.apache.org`
- 添加第三方模组依赖见 `build.gradle` 的 `dependencies` 块内注释示例（含 JEI 的写法）
- 运行时才需要的依赖用 `localRuntime` 而不是 `runtimeOnly`（模板已配置好该 dependency configuration）
