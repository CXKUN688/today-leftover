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
然后执行 `:core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`。

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
.\gradlew.bat :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

## 5. 构建产物

| 产物 | 路径 |
| --- | --- |
| Debug APK | `app\build\outputs\apk\debug\app-debug.apk` |
| Lint 文本报告 | `app\build\reports\lint-results-debug.txt` |
| 单元测试报告 | `core\build\test-results\test\*.xml` |

## 6. 模块结构

- `:core` — Kotlin/JVM 纯逻辑模块，无 Android 依赖。
  预算与日均算法（`BudgetCalculator.kt`、`Ledger.kt`）、账单解析与通知识别（`Imports.kt`）、备份编解码（`Backup.kt`）、数据模型（`Models.kt`）。
- `:app` — Android 应用模块。
  Compose 界面（`ui/`）、持久化状态（`data/BudgetStore.kt`）、通知监听服务（`PaymentListenerService.kt`）。

## 7. 已知构建环境问题

### 7.1 模拟器无法在本机运行

`emulator -accel-check` 返回码 6：`Android Emulator hypervisor driver is not installed on this machine`。
机器上既没有 AEHD（Android Emulator hypervisor driver），也没有启用 Hyper-V/WHPX，
因此模拟器只能以 `-accel off` 纯软件方式启动 x86_64。

更关键的是，QEMU 后端二进制本身无法启动：

```
D:\CodexTasks\android-toolchain\android-sdk\emulator\qemu\windows-x86_64\qemu-system-x86_64-headless.exe --version
→ 退出码 -1073741515 (0xC0000135, STATUS_DLL_NOT_FOUND)，无任何输出
```

而模拟器前端 `emulator.exe -version` 可以正常输出版本号，
说明是 QEMU 后端缺少依赖 DLL，而非模拟器整体损坏。

**结论：本机无法完成模拟器验证。** 需要管理员权限安装 AEHD 驱动或修复 VC++ 运行库后重试，
或改用物理设备（USB 调试）完成设备验证。详见 `docs/VERIFICATION.md`。

### 7.2 adb 需要正确的用户目录

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
