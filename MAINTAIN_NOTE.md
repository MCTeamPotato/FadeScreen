# FadeScreen 维护笔记

给后来改这个模组的人（包括我自己）：这里记的是**为什么这么写**、哪些地方一动就会坏、
以及每个坑当时的表现和修法。改代码前先看第 2 节的不变量。

- 目标版本：Forge 1.20.1（`gradle.properties` 里 `loom.platform = forge`，MC 1.20.1），其他版本均不确定行为是否一致。
- 全部逻辑在客户端；`fadescreen.mixins.json` 的 `client` 列表里注册
- 入口：[`FadeScreen.java`](src/main/java/me/kall/fadescreen/FadeScreen.java)（只注册客户端配置）

---

## 1. 总体架构

一句话：**没有过渡时和原版一模一样；只在过渡的几百毫秒里把界面渲染进离屏帧缓冲，再用统一透明度贴回来。**

数据流（一次切换）：

```
Minecraft#setScreen
  └─ ScreenFader.onScreenChanged(prev, next)
       记录是否要做过渡（fadePending）、上一屏要不要做 ghost

ForgeHooksClient#drawScreen（每帧）
  ├─ HEAD   → beginScreen()
  │            needsComposite? 否 → 直接返回，界面照常画到主画面（直通）
  │            是 → 需要时先 captureGhost()，再把 content FBO 清空并绑定
  ├─ ...    → 界面正常渲染（包含 Forge 的 ScreenEvent.Render.Pre / Post）
  └─ RETURN → endScreen()
                flush → 绑回主画面 → 用常量 alpha 合成 content FBO

GameRenderer#render（每帧，GuiGraphics.flush 之后）
  └─ afterGui()
       需要时捕获 ghost → 把上一屏按 1-alpha 叠在最上层
       → makeWindowOpaque() 把主目标 alpha 补回 1
```

两种「上一屏」的来源：

- `contentValid == true`：content FBO 里就是上一屏最后一帧 → 直接拷贝当 ghost；
- `contentValid == false`（静止时我们直通，没有留帧）→ **把上一屏重新渲染一次**到 ghost FBO。

---

## 2. 不变量（改之前务必确认，破坏了就会出现具体的视觉 bug）

1. **合成不能用 FBO 的 alpha 通道。**
   原版 GUI 的 `RenderSystem.defaultBlendFunc()` 是
   `SRC_ALPHA/ONE_MINUS_SRC_ALPHA` + **`ONE/ZERO`**——alpha 是「被源 alpha 覆盖」而不是「覆盖率累加」。
   天空渐变（`CubeMap` 顶点色 alpha）、抗锯齿的 Logo 边缘都会在 FBO 里留下很低的 alpha，
   如果按预乘 alpha（`ONE / ONE_MINUS_SRC_ALPHA`）合成，就会把 RGB 直接加上去 → 白色描边/颗粒边线。
   → 必须用 `glBlendColor` + `CONSTANT_ALPHA` 做统一透明度。
   （实现：[`ScreenFader.drawTexture`](src/main/java/me/kall/fadescreen/client/ScreenFader.java:403)）

2. **不能把主画面的 alpha 写低。**
   这个窗口在部分环境按 alpha 合成，屏幕淡到 30% 时窗口也会变 70% 透明，背后的东西（早期加载画面、
   桌面）会透出来。→ 合成时 `colorMask(true,true,true,false)`，并在帧末
   [`makeWindowOpaque()`](src/main/java/me/kall/fadescreen/client/ScreenFader.java:259) 只写 alpha 通道补回 1。

3. **静止时必须直通。**
   [`needsComposite()`](src/main/java/me/kall/fadescreen/client/ScreenFader.java:166) 为假时直接返回，
   不进离屏、不合成。这是「稳态和原版逐像素一致」的保证，也是排查视觉差异的第一道分界线。

4. **淡入的时间轴从「该界面第一帧真正被画出来」开始计时。**
   不能记在 `setScreen` 里：打开重界面（背包在整合包里、Embeddium 选项页）时首帧可能来得晚，
   整个淡入会被吃掉。→ `fadePending` + [`beginClockIfPending()`](src/main/java/me/kall/fadescreen/client/ScreenFader.java:171)。
   没有 `beginScreen` 的情况（回到世界）由 `afterGui` 启动时钟。

5. **同一帧里被替换掉的界面要 retarget，不要取消过渡。**
   创造模式按 E 会先 `InventoryScreen` 再换成 `CreativeModeInventoryScreen`；
   Embeddium 会先 `SodiumOptionsGUI` 再换成 `EmbeddiumVideoOptionsScreen`。
   旧界面从没显示过 → 不需要 ghost，但**新界面必须继续淡入**。
   （`previousPresented` / `renderedFrames`，见 [`onScreenChanged`](src/main/java/me/kall/fadescreen/client/ScreenFader.java:67)）

6. **首个界面跳过我们的淡入。**
   原版 TitleScreen 自带 fade，叠加会出现「先暗再亮」；且那一刻背景缓冲里可能还是早期加载画面。

7. **`Screen#renderBackground` 之后必须重绑离屏。**
   Modern UI 的 `BlurHandler` 会跑后处理链并把**主帧缓冲**绑回去，之后界面内容（包括 MUI 自己的布局）
   全画到主画面上，我们合成的是空图 →「没有淡入」。
   → [`rebindTargetIfCapturing()`](src/main/java/me/kall/fadescreen/client/ScreenFader.java:184) 由 `ScreenMixin` 调用。
   任何 mod 只要在背景绘制里重绑 framebuffer，这条对它同样生效。

8. **重入保护。**
   - `nesting`：界面里再渲染界面时只允许最外层做事；
   - `capturingGhost`：重渲染上一屏时不要再进入一次捕获；
   - `ScreenMixin` 里的重渲染走的是 `ForgeHooksClient.drawScreen`，会被上面的标志挡住。

9. **合成窗口要包住 Forge 的 Render.Pre/Post。**
   MUI 这类界面会在 `ScreenEvent.Render.Post` 提交几何体，而 Post 在 `Screen#renderWithTooltip` 返回之后。
   → 注入点选 `ForgeHooksClient#drawScreen`（HEAD/RETURN），不要退回 `renderWithTooltip`。

---

## 3. 文件职责

| 文件 | 职责 |
| --- | --- |
| [`ScreenFader.java`](src/main/java/me/kall/fadescreen/client/ScreenFader.java) | 全部渲染与状态机：[`onScreenChanged`](src/main/java/me/kall/fadescreen/client/ScreenFader.java:67)、[`beginScreen`](src/main/java/me/kall/fadescreen/client/ScreenFader.java:121)、[`endScreen`](src/main/java/me/kall/fadescreen/client/ScreenFader.java:192)、[`afterGui`](src/main/java/me/kall/fadescreen/client/ScreenFader.java:218)、[`captureGhost`](src/main/java/me/kall/fadescreen/client/ScreenFader.java:349)、[`drawTexture`](src/main/java/me/kall/fadescreen/client/ScreenFader.java:403) |
| [`FadeConfig.java`](src/main/java/me/kall/fadescreen/client/FadeConfig.java) | `config/fadescreen-client.toml`（`enabled` / `durationMillis` / `skippedScreens`） |
| [`ClientHooksMixin.java`](src/main/java/me/kall/fadescreen/mixin/ForgeHooksClientMixin.java) | 包住 `drawScreen`（含 Pre/Post），离屏开始/结束 |
| [`ScreenMixin.java`](src/main/java/me/kall/fadescreen/mixin/ScreenMixin.java) | `renderBackground` 返回后重绑离屏（MUI 兼容） |
| [`MinecraftMixin.java`](src/main/java/me/kall/fadescreen/mixin/MinecraftMixin.java) | `setScreen` HEAD，安排过渡 |
| [`GameRendererMixin.java`](src/main/java/me/kall/fadescreen/mixin/GameRendererMixin.java) | 在 `GuiGraphics.flush()` 之后画 ghost / 补窗口 alpha |

---

## 4. 踩过的坑（现象 → 原因 → 修法）

| 现象 | 原因 | 修法 |
| --- | --- | --- |
| 首次进游戏 TitleScreen 闪一下，Logo 有白边 | 我们自己又淡入了一遍，和原版 fade 叠加；半透明时透出早期加载画面 | 首屏跳过 + 主目标 alpha 不写低 |
| 屏幕淡入时整个窗口变透明，透出桌面/加载画面 | 合成把主画面 alpha 写成了 0.3 之类 | 合成只写 RGB + 每帧 `makeWindowOpaque()` |
| 打开背包 / Embeddium 视频设置不淡入 | 同一帧内 `setScreen` 被调用两次，旧界面一帧都没显示过 | 改为 retarget，保留已排好的过渡 |
| 过渡时黑描边外出现白色点状边线 | FBO alpha 不是覆盖率，预乘合成把 RGB 直接加起来 | 常量 alpha 合成（第 2 节第 1 条） |
| 界面打开有淡出没淡入 / 卡顿后没有淡入 | 时间轴记在 `setScreen`，首帧来晚就被吃掉 | 时间轴移到首帧 |
| 装 Modern UI 后 ESC / 视频设置无淡入，关界面无淡出 | MUI 的 `BlurHandler` 重绑主帧缓冲；其内容在 Render.Post 提交 | `ScreenMixin` 重绑 + 注入点改 `drawScreen` |
| `run/config` 里改成 3000ms 但没生效 | 配置是启动时读的 | 改完重启 |

---

## 5. 兼容性

- **Forge `ForgeHooksClient.drawScreen` 是 1.20.1 唯一能包住 `ScreenEvent.Render.Post` 的挂载点。**
  换 NeoForge 1.21 时这个类不存在，需要找等价位置；退回到 `Screen#renderWithTooltip` 会重新丢掉
  Post 阶段画界面的 mod（MUI 就是典型）。
- **Modern UI 3.12.0.1** 的关键行为：
  - 界面是真正的 `Screen`（`MuiScreen` + `createScreen`），会走 `Minecraft#setScreen`；
  - `BlurHandler.drawScreenBackground` 会 `getMainRenderTarget().bindWrite(true)`，且不还原；
  - 有一套界面黑名单，黑名单里的界面不走模糊（所以资源包界面一直正常，可以作为对照）。
- 任何在 `renderBackground` 里重绑 framebuffer 的 mod，都会因为第 2 节第 7 条被兜住。
- 本项目 `build.gradle` 里还挂着 MixinExtras 的 shadow/relocate，但当前代码**没有用到**它；
  如果要用 `@Local` 等特性，需要把 `mixinextras-forge` 真正放进运行时依赖（只 shadow 到发布 jar 是
  加载不出来的，dev 运行会 `NoClassDefFoundError`）。

---

## 6. 配置

`config/fadescreen-client.toml`（默认值）：

```toml
enabled = true
durationMillis = 200   # 单次过渡时长；一次切换是交叉淡化，不是淡出+淡入两段；0 关闭动画
skippedScreens = []    # 全限定类名，例如 "net.minecraft.client.gui.screens.LevelLoadingScreen"
```

`durationMillis` 是运行时每帧读的，但改文件仍需重启（Forge 配置加载时机）。

---

## 7. 已知限制 / 可选后续

- **过渡期间整屏统一淡化**：界面自身的半透明区域在这 200ms 内不会透出世界（离屏背景是黑的），
  淡入结束切回直通后恢复正常。要连背景一起淡，需要在屏幕绘制前把主画面拷一份当底再混合。
- `Overlay`（加载世界、下载地形）不是 `Screen`，不参与。
- 淡入期间界面已经可交互；要屏蔽可以在 `mouseClicked`/`keyPressed` 里按当前 alpha 拦。
- 目前只有全局时长；按界面类型区分配置、或做成游戏内配置界面都还没做。
- FBO 在窗口尺寸变化时会重建（`ensureTargets`），极小概率在 resize 那一帧出现一张空帧。

---

## 8. 调试方法

- **不要用 `System.out.println` 打日志**：部分整合包的日志配置不记录 stdout（`[STDOUT/]` 计数为 0），
  排查时会误以为代码没跑。用 `LoggerFactory.getLogger("FadeScreen")`，会出现在 `latest.log`。
- 排查三类问题的临时日志：
  - `change A -> B frames=N firstSeen=...`：`setScreen` 是否触发、上一屏显示过多少帧；
  - `begin X composite=true/false pending=...`：该界面第一帧是否走了离屏；
  - 有 `change` 但没排上过渡 → 被哪个早退分支挡了。
- **截图对比要注意**：标题界面全景相机在转、splash 会缩放，逐像素 diff 只对静态元素有意义；
  比较 Logo 这类静态元素时，先裁区域再看最大差。
- 验证最小路径：标题 → 单人游戏（淡出）、取消回标题（淡入）、进世界 → ESC（MUI 场景）、
  ESC → 视频设置、ESC → 资源包（MUI 黑名单对照）、回到游戏（淡出）、按 E 开背包（同帧换屏）、
  `enabled = false` 作为「原版对照」。

---

## 9. 构建与部署（本机环境）

```powershell
$env:JAVA_HOME = 'D:\ZuluJava21'          # Forge 1.20.1 需要 JDK 17+，本机用 Zulu 21 跑 Gradle
& "D:\ModDev\FadeScreen\gradlew.bat" -p "D:\ModDev\FadeScreen" --offline build
```

- 产物：`build/libs/fadescreen-1.20.1-1.0.0.jar`（`remapJar` 后的正式包；`-dev-shadow.jar` 不要分发）
- 测试实例：`C:\Users\lenovo\AppData\Roaming\PrismLauncher\instances\NarutoClient\minecraft\mods\fadescreen-1.20.1-1.0.0.jar`
  （可直接覆盖，无需备份）
- 开发运行：`gradlew runClient`；`run/config/fadescreen-client.toml` 是 dev 环境自己的配置
- `--offline` 可用是因为本机已有完整的 Loom/Forge 缓存；换机器要去掉