# Touys · Minecraft 26.3 Fabric

基于 [KyanBirb/Touys](https://github.com/KyanBirb/touys) 的 Kotlin 移植，为 [Sable 26.3](https://github.com/NightEpiphany/sable-port) 子世界（sub-level）提供装配、克隆、推力、删除、拍照和运动录制工具。模组同时支持客户端、单人游戏和独立服务端；本项目的目标平台为 Minecraft 26.3 Fabric。

## 安装

准备以下运行环境，将 Touys、Sable、Fabric API 和 Fabric Language Kotlin 安装到客户端与服务端的 `mods` 目录：

| 依赖 | 本项目使用的版本 |
| --- | --- |
| Minecraft | 26.3 |
| Java | 25 或更新版本，开发与 CI 使用 JDK 25 |
| Fabric Loader | 0.19.5 或更新版本 |
| Fabric API | 0.162.0+26.3 |
| Fabric Language Kotlin | 1.14.1+kotlin.2.4.20 |
| Sable 26.3 Fabric | 3.0.0，来自 [26.3 移植仓库](https://github.com/NightEpiphany/sable-port) |
| Touys | `build/libs/Touy-26.3-fabric-1.0.5-26.3.jar` |

开发时使用的 Sable 二进制位于 `libs/sable-fabric-26.3-3.0.0.jar`。发布的 Touys jar 需要单独安装 Sable；`-sources.jar` 用于查看源码。Sable 依赖包包含 Windows、Linux 和 macOS 的 x86_64 / aarch64 Rapier 原生库。

在创造模式的「Sable 物理玩具」物品栏中取得工具；悬停物品时按住 Shift 可查看说明。也可使用 `/give @s touys:clone_gun` 等命令，物品 ID 见下表。当前项目没有添加合成配方。

## 工具操作

默认工具作用距离为 80 格，服务端校验目标、可操作权限和装配/克隆选区。

| 工具 / ID | 操作与效果 |
| --- | --- |
| 泡泡吹管 `touys:bubble_blower` | 右键方块设置起点，再次右键确定终点，将长方体区域内的非空气方块装配为可运动的子世界。潜行右键清除选区。两点须位于同一维度、同一个方块网格，不能跨越主世界与不同子世界。 |
| 克隆枪 `touys:clone_gun` | 潜行右键复制指向的子世界；没有目标时清除副本。普通右键在指向位置放置副本，朝向随玩家转向调整，并显示放置预览。保存方块、方块实体和计划 Tick；副本包围盒体积与 NBT 大小均有限制。 |
| 撬棍 `touys:crowbar` | 左键子世界，通过 Sable 的推力属性推动它；右键目标可直接施加冲量。默认推力属性为 +100%；在物品栏中将另一把撬棍放到此物品上可合并力度，并消耗另一把撬棍，最高 +40000%。 |
| 邪恶之球 `touys:evil_ass_orb` | 按住右键立即移除指向的整个子世界，持续使用时每 5 Tick 再次检查目标。包含球体开合动画、声音和粒子。 |
| 照相机 `touys:camera` | 右键打开取景界面，按住鼠标左键拖框选择子世界，松开鼠标拍摄并得到照片。默认最多拍摄 128 个目标；空选区或超出上限时可重新框选。界面不会暂停物理模拟。 |
| 照片 `touys:photo` | 右键将拍摄到的子世界恢复至记录的位置和旋转，并清空线速度与角速度。照片关联拍摄时维度与子世界 UUID；恢复时，对应子世界须仍存在。可插入投影仪。 |
| 摄像机 `touys:camcorder` | 右键指向的子世界开始录制，每个世界 Tick 记录一次位置和旋转；再次右键结束并得到磁带。录制中潜行右键丢弃记录。手持时显示录制时间。 |
| 磁带 `touys:tape` | 右键向指向的子世界回放运动。潜行右键设置追踪目标，没有目标时清除追踪；设置追踪后，普通右键会向该目标回放。磁带保存世界坐标下的位置和旋转，可用于另一个子世界，也可插入投影仪。 |

装配和克隆默认最多允许 65,536 格的包围盒体积，包含包围盒内的空气。克隆数据另外限制为 1 MiB。达到录制时长上限后，摄像机保留已记录帧，需再次右键保存磁带。未结束的录制和正在进行的回放属于服务器运行状态，停服时清理；已保存的照片、磁带和克隆数据随物品持久化。

## 投影仪

投影仪 ID 为 `touys:projector`，可朝六个方向放置。

1. 手持照片或磁带右键插入；已有物品时交换，原物品返回背包。
2. 空手右键取出内容；背包已满时掉落到世界中。
3. 红石信号从无到有时激活一次。持续供能不会重复触发；断电后重新供能可再次激活。

照片直接恢复关联的子世界。已追踪目标的磁带直接向该目标回放；未追踪目标的磁带沿投影仪朝向发出最长 16 格的射线，命中子世界后启动回放。投影仪保存插入的物品，破坏时掉落方块与内容。

## JSON5 配置与诊断

首次启动生成 `config/touys.json5`。支持注释、单引号、未加引号的键和末尾逗号，例如：

```json5
{
  item_range: 80,
  assembly_max_blocks: 65536,
  clone_max_blocks: 65536,
  recording_max_duration: 6000,
  recording_inactive_ticks: 2400,
  recording_max_sessions: 16,
  max_snapshot_sub_levels: 128,
}
```

| 配置项 | 默认值 | 范围与含义 |
| --- | --- | --- |
| `item_range` | 80 | 1–256，工具目标距离，单位为格。 |
| `assembly_max_blocks` | 65536 | 1–65536，装配选区的包围盒体积上限。 |
| `clone_max_blocks` | 65536 | 1–65536，克隆子世界的包围盒体积上限。 |
| `recording_max_duration` | 6000 | -1–12000，开始录制时分配的帧容量。20 帧约为 1 秒；默认约 5 分钟，-1 使用 12000 帧的硬上限，约 10 分钟；0 按至少 1 帧处理。 |
| `recording_inactive_ticks` | 2400 | -1–2147483647，目标卸载或摄像机不再更新时的超时清理 Tick 数；-1 关闭超时，0 按 1 Tick 处理。默认约 2 分钟。 |
| `recording_max_sessions` | 16 | 1–64，每个维度的同时录制数量上限与同时回放数量上限。 |
| `max_snapshot_sub_levels` | 128 | 1–128，一张照片的最大目标数。 |

服务端每 20 Tick 检查配置文件变化，将有效配置作为完整快照替换。无效语法、越界值、重复键或超过 64 KiB 的文件会记录警告，并保留上次有效配置。客户端的选区预览使用客户端配置，实际操作受服务端配置限制。

具有管理员命令权限的玩家或服务端控制台可使用：

```text
/touys reload
/touys diagnostics
```

`reload` 立即重载配置；`diagnostics` 输出 Touys/Sable 版本、运行环境、当前配置与录制/回放统计。操作失败信息通过 SLF4J 写入游戏日志；崩溃报告追加缓存的 `Touys diagnostics`，便于追查环境与会话状态。

## 构建与测试

使用 JDK 25 和仓库自带的 Gradle 9.7.1 wrapper。构建需要能访问 Fabric Maven、Maven Central 和 Minecraft 下载服务，并保留 `libs` 中的 Sable 二进制。

Windows PowerShell：

```powershell
./gradlew.bat build --no-daemon --console=plain
./gradlew.bat test --no-daemon --console=plain
./gradlew.bat runGameTest --no-daemon --console=plain
./gradlew.bat runClient
```

Linux / macOS：

```sh
chmod +x ./gradlew
./gradlew build --no-daemon --console=plain
./gradlew test --no-daemon --console=plain
./gradlew runGameTest --no-daemon --console=plain
./gradlew runClient
```

`build` 包含 JUnit 与独立服务端 Fabric GameTest；`test` 和 `runGameTest` 用于分别运行两组测试。最终模组与源码 jar 输出到 `build/libs/`。GameTest 使用独立的 `touys_test` 测试模组和 `build/run/gameTest/` 运行目录，测试代码不打入发布模组。

JUnit 覆盖 JSON5 与配置热加载、选区体积及边界、克隆数据验证、姿态编码、录制容量、回放帧游标和超时生命周期。GameTest 覆盖真实 Sable 刚体、装配及箱子内容迁移、克隆后的方块实体和计划 Tick、照片恢复、磁带回放与事件驱动、投影仪库存/掉落/红石触发，以及崩溃诊断 Mixin。

JUnit 报告位于 `build/reports/tests/test/` 和 `build/test-results/test/`；GameTest 日志位于 `build/run/gameTest/logs/`。客户端 GameTest 未启用；使用 `runClient` 检查取景框、闪光、工具预览、撬棍颜色和球体动画。GitHub Actions 在 Ubuntu / JDK 25 上运行构建与 GameTest，并保存 jar、测试报告和运行日志。

## 移植说明

代码按职责分为 `core`、`item`、`block`、`event`、`network`、`util`、`mixin` 和 `client`。录制采用有界的 primitive 数组；回放复用约束与向量，按世界 Tick 推进帧，在物理子步中更新同一个约束。公共初始化与物理逻辑可在独立服务端加载。诊断 Mixin 使用 Mixin Extras `@WrapOperation`。

原版基于 Veil 的球体开合渲染已移植为 Minecraft 原生物品模型属性及 21 帧模型近似。原版外部 locker 的回放结束自动锁定功能没有对应的标准 Sable API，本版回放结束会清空速度并释放回放约束，随后继续物理模拟。旧版 1.21.1 的物品组件与 Sable 存档没有自动格式迁移器。

## 许可与来源

原 Touys 源码、模型、纹理与声音来自 [KyanBirb/Touys](https://github.com/KyanBirb/touys)，遵循 MIT 协议，保留 `Copyright (c) 2025 KyanBirb`。本仓库包含 `LICENSE` 与 `NOTICE`，构建时一并打入 jar。

物理引擎为 [ryanhcode/Sable](https://github.com/ryanhcode/sable)，26.3 版本来自 [NightEpiphany/sable-port](https://github.com/NightEpiphany/sable-port)。Sable 作为独立依赖使用，适用其自身许可。
