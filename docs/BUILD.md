# 今日余粮 — 构建说明

本文说明如何在本机复现构建，以及工具链位置与版本锁定情况。

## 1. 项目位置

| 项目 | 路径 |
| --- | --- |
| 工作树（源码） | `D:\CodexTasks\daily-budget-android-dev` |
| 主仓库（git 目录） | `D:\CodexTasks\daily-budget-android` |
| 工具链根目录 | `D:\CodexTasks\android-toolchain` |
| 交付物 | `D:\CodexTasks\daily-budget-android-dev\deliverables` |

> `daily-budget-android-dev` 是 `daily-budget-android` 仓库的一个 git worktree，
> 其 `.git` 是指向 `D:\CodexTasks\daily-budget-android\.git\worktrees\daily-budget-android-dev` 的指针文件。

## 2. 工具链位置与版本

所有工具都安装在 D 盘，不依赖系统环境变量，也不覆盖任何既有工具。

| 组件 | 版本 | 路径 |
| --- | --- | --- |
| JDK | 17.0.20.1+1 | `D:\CodexTasks\android-toolchain\jdk\jdk-17.0.20.1+1` |
| Gradle | 8.13（wrapper 自动下载） | `D:\CodexTasks\android-toolchain\gradle-cache\wrapper` |
| Gradle 用户目录 | 项目专用 | `D:\CodexTasks\android-toolchain\gradle-cache` |
| Android SDK | platform 36 / build-tools 35.0.0 | `D:\CodexTasks\android-toolchain\android-sdk` |
| platform-tools (adb) | — | `D:\CodexTasks\android-toolchain\android-sdk\platform-tools` |
| Android Emulator | 37.1.11.0 | `D:\CodexTasks\android-toolchain\android-sdk\emulator` |
| AVD | BudgetTest (android-35, x86_64) | `D:\CodexTasks\android-toolchain\avd\BudgetTest.avd` |
| AVD 用户目录 | — | `D:\CodexTasks\android-toolchain\android-user` |

SDK 路径通过 `local.properties` 固定：

```
sdk.dir=D:/CodexTasks/android-toolchain/android-sdk
```

## 3. 依赖版本（`app/build.gradle.kts`）

- Compose BOM `2025.04.01`
- activity-compose `1.10.1`
- Room `2.7.1`（runtime / ktx / compiler via KSP）
- lifecycle-runtime-compose `2.8.7`
- kotlinx-serialization-json `1.8.1`
- androidx.test.ext:junit `1.2.1`、androidx.test:runner `1.6.2`

应用构建参数：`compileSdk=36`、`minSdk=28`、`targetSdk=36`、`versionCode=1`、`versionName="0.1.0"`、JVM target 17。

## 4. 构建命令

### 4.1 使用构建脚本（推荐）

```powershell
powershell -ExecutionPolicy Bypass -File D:\CodexTasks\daily-budget-android-dev\scripts\build.ps1
```

脚本会自动设置 `JAVA_HOME`、`GRADLE_USER_HOME`、`ANDROID_HOME`，
然后执行 `:core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest`。

> `:app:assembleDebugAndroidTest` 用于编译设备测试 APK。
> 它**不需要**连接设备，但能捕获设备测试代码的编译错误——
> 曾有一处导入冲突使设备测试长期无法编译却未被发现（见 `docs/VERIFICATION.md` §3.1）。

加上 `-DeviceTests` 会追加 `:app:connectedDebugAndroidTest`（需要已连接的设备）：

```powershell
powershell -ExecutionPolicy Bypass -File D:\CodexTasks\daily-budget-android-dev\scripts\build.ps1 -DeviceTests
```

### 4.2 手动执行

```powershell
$env:JAVA_HOME='D:\CodexTasks\android-toolchain\jdk\jdk-17.0.20.1+1'
$env:GRADLE_USER_HOME='D:\CodexTasks\android-toolchain\gradle-cache'
$env:ANDROID_HOME='D:\CodexTasks\android-toolchain\android-sdk'
cd D:\CodexTasks\daily-budget-android-dev
.\gradlew.bat :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

### 4.3 设备测试（需要真机或模拟器）

仅编译设备测试、不执行：

```powershell
.\gradlew.bat :app:assembleDebugAndroidTest
```

### 4.4 一键设备验证脚本

连接真机后，推荐使用 `scripts\verify-device.ps1`，
它会自动完成设备信息记录、安装、启动冒烟测试、设备测试与日志收集，
并把证据写入 `evidence\` 目录：

```powershell
powershell -ExecutionPolicy Bypass -File D:\CodexTasks\daily-budget-android-dev\scripts\verify-device.ps1
```

可选参数：`-SkipTests`（跳过设备测试）、`-SkipInstall`（跳过安装）。

未检测到设备时，脚本会给出排查提示并以非零码退出，不会静默失败。

> 注意：该脚本含中文输出，文件必须保存为 **UTF-8 with BOM**，
> 否则 Windows PowerShell 5.1 会按 ANSI 解析导致乱码与语法错误。

## 4.5 应用图标

图标资源位于 `app/src/main/res/`：

| 资源 | 用途 |
| --- | --- |
| `mipmap-anydpi-v26/ic_launcher.xml` | 自适应图标（Android 8.0+），引用背景层与前景层 |
| `mipmap-anydpi-v26/ic_launcher_round.xml` | 圆形自适应图标 |
| `drawable/ic_launcher_background.xml` | 矢量背景层（品牌绿渐变 `#176957` → `#0E4A3D`） |
| `mipmap-*/ic_launcher_foreground.png` | 前景层（饭碗图案），五档密度 |
| `mipmap-*/ic_launcher.png` | 旧版圆角方形图标（API < 26 回退） |
| `mipmap-*/ic_launcher_round.png` | 旧版圆形图标（API < 26 回退） |

设计说明：

- 图形为**饭碗 + 米堆 + 热气**，呼应「今日余粮」的含义
- 背景层使用品牌绿渐变，与 `ui/Theme.kt` 的 `Teal #176957` 一致
- 米堆使用金色渐变，突出「余粮」的视觉重点
- 前景层图形控制在自适应图标**安全区内**（约 62%～82%），
  确保在圆形、方形、水滴形等各种启动器遮罩下都不被裁切
- 同时提供 `<monochrome>`，支持 Android 13+ 主题图标（Material You）

**修改图标后需重新构建并重新生成交付物与校验值**（见 `docs/VERIFICATION.md`）。

## 5. 构建产物

| 产物 | 路径 |
| --- | --- |
| Debug APK | `app\build\outputs\apk\debug\app-debug.apk` |
| 设备测试 APK | `app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk` |
| Lint 文本报告 | `app\build\reports\lint-results-debug.txt` |
| 单元测试报告 | `core\build\test-results\test\*.xml` |
| 设备验证证据 | `evidence\`（由 `verify-device.ps1` 生成，不纳入版本控制） |

## 6. 模块结构

- `:core` — Kotlin/JVM 纯逻辑模块，无 Android 依赖。
  预算与日均算法（`BudgetCalculator.kt`、`Ledger.kt`）、账单解析与通知识别（`Imports.kt`）、备份编解码（`Backup.kt`）、数据模型（`Models.kt`）。
- `:app` — Android 应用模块。
  Compose 界面（`ui/`）、持久化状态（`data/BudgetStore.kt`）、通知监听服务（`PaymentListenerService.kt`）。

## 7. 已知构建环境问题

### 7.1 模拟器无法完成启动（客户机不能执行）

**先说结论：本机模拟器进程可以正常起来，但客户机无法引导，因此不能用于验证。**

#### 7.1.1 启动模拟器必须修正 PATH（重要）

QEMU 后端二进制依赖模拟器目录存在于 PATH，否则会**静默秒退、无任何输出**
（容易被误判为「二进制损坏」或「缺少 DLL」，实测均不成立）。
注意该二进制也不支持 `--version`。

正确的启动方式：

```powershell
$emu='D:\CodexTasks\android-toolchain\android-sdk\emulator'
$env:PATH="$emu;$emu\lib64;$emu\qemu\windows-x86_64;$env:PATH"
$env:ANDROID_AVD_HOME='D:\CodexTasks\android-toolchain\avd'
$env:ANDROID_USER_HOME='D:\CodexTasks\android-toolchain\android-user'
$env:ANDROID_SDK_ROOT='D:\CodexTasks\android-toolchain\android-sdk'
$env:HOME='D:\CodexTasks\android-toolchain\android-user'
$env:USERPROFILE='D:\CodexTasks\android-toolchain\android-user'

& "$emu\emulator.exe" -avd BudgetTest -no-window -no-audio -no-boot-anim `
    -no-snapshot -gpu swiftshader -accel off -memory 2048 -cores 2
```

修正 PATH 后设备会出现在 adb 中：

```
$ adb devices
emulator-5554   offline
```

#### 7.1.2 但客户机无法执行（决定性阻塞）

设备停在 `offline`，且 QEMU 的 **CPU 时间完全不增长**（20 秒内 0.375s → 0.375s），
说明 vCPU 被冻结而非运行缓慢。模拟器自身警告：

```
WARNING | x86_64 emulation may not work without hardware acceleration!
```

硬件加速不可用：`emulator -accel-check` 返回 6（未安装 AEHD），
`extras/` 下**没有**驱动安装包，`HvHost` 服务为 `Stopped`，
且当前用户**无管理员权限**（`IsAdmin=False`），无法安装内核态驱动。

**结论：需要管理员权限安装 AEHD/WHPX 才能用模拟器。**

> **验证已改用物理设备完成。** 真机（小米 15 / Android 17）验证结果见
> `docs/VERIFICATION.md`，设备测试 9 tests / 0 failures / 4 skipped。
> 按原计划，真机结果本就优先于且不可由模拟器替代，因此模拟器不可用不构成交付阻塞。

详见 `docs/VERIFICATION.md` 第 5 节（含排查过程与已推翻的错误结论）。

### 7.2 真机验证步骤（推荐路径）

```powershell
# 1. 确认设备已授权
D:\CodexTasks\android-toolchain\android-sdk\platform-tools\adb.exe devices -l

# 2. 一键验证（推荐）
powershell -ExecutionPolicy Bypass -File scripts\verify-device.ps1
```

小米（HyperOS）需特别注意：

- 开发者选项中「USB 调试」**和**「USB 调试（安全设置）」都要开启
- 「USB 调试（安全设置）」用于允许通过 USB 调试模拟点击，Compose UI 测试依赖它
- 若 adb 看不到设备，检查手机是否暴露了 `ADB Interface`
  （仅 MTP/`WPD` 类说明 ADB 未开启）：

  ```powershell
  Get-PnpDevice -PresentOnly | Where-Object { $_.InstanceId -like '*VID_2717*' }
  ```

### 7.3 adb 需要正确的用户目录

模拟器/adb 默认写入 `C:\Users\<用户>\.android`。
如需指向 D 盘用户目录，设置：

```powershell
$env:ANDROID_USER_HOME='D:\CodexTasks\android-toolchain\android-user'
$env:ANDROID_AVD_HOME='D:\CodexTasks\android-toolchain\avd'
```

## 8. 不纳入版本控制的内容

以下内容已被 `.gitignore` 排除，不应提交：

- Gradle 缓存与 wrapper 下载（`D:\CodexTasks\android-toolchain\gradle-cache`）
- Android SDK 与系统镜像
- 调试签名密钥（`~/.android/debug.keystore`）
- 构建输出（`build/`、`.gradle/`、`.kotlin/`）
