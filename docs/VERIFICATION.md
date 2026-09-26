# 今日余粮 — 验证证据与缺口

本文如实记录当前交付的验证状态。**明确区分「已验证通过」与「未验证」**，
未验证项一律不记为通过。

**验证日期**：2026-09-26
**交付版本**：0.1.0 (versionCode 1)
**APK SHA-256**：`9728f6ee6ea2911752d6635b695848a3772beb851884dae4a6dd513f863b9c12`

---

## 1. 验证状态总览

| 验证项 | 状态 | 证据 |
| --- | --- | --- |
| core 纯逻辑单元测试 | ✅ 通过 | 42 个测试，0 失败 |
| app 单元测试 | ⚠️ 无测试源（NO-SOURCE） | — |
| Android Lint | ✅ 通过 | 0 errors, 12 warnings |
| assembleDebug 构建 | ✅ 通过 | APK 已产出 |
| 模拟器启动 | ❌ **失败（环境阻塞）** | 见 §4 |
| `connectedDebugAndroidTest` | ❌ **未执行** | 无可用设备 |
| 模拟器全流程手动操作 | ❌ **未执行** | 无可用设备 |
| 真机安装与启动 | ❌ **未执行** | 无设备连接 |
| 真实微信/支付宝通知记账 | ❌ **未验证** | 需真机 + 真实通知 |
| 真实平台账单导入 | ❌ **未验证** | 仅合成样本通过 |
| 小米 15 / Android 17 兼容 | ❌ **未实测** | 无设备 |

---

## 2. 构建验证（已验证）

执行命令：

```powershell
.\gradlew.bat :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

结果：

```
BUILD SUCCESSFUL in 3s
53 actionable tasks: 5 executed, 48 up-to-date
```

### 2.1 core 单元测试明细（42 个全部通过）

| 测试类 | 用例数 | 失败 | 错误 | 跳过 |
| --- | --- | --- | --- | --- |
| `BudgetTest` | 8 | 0 | 0 | 0 |
| `AlertTest` | 6 | 0 | 0 | 0 |
| `LedgerTest` | 9 | 0 | 0 | 0 |
| `ImportTest` | 10 | 0 | 0 | 0 |
| `BackupTest` | 3 | 0 | 0 | 0 |
| `RegressionTest` | 6 | 0 | 0 | 0 |
| **合计** | **42** | **0** | **0** | **0** |

报告位置：`core\build\test-results\test\TEST-*.xml`。

### 2.2 已覆盖的关键行为（单元测试层面）

`RegressionTest` 中直接对应计划 Review Focus 的用例：

- `sameCandidateTwiceDoesNotDuplicateQueue` — 同一候选重复入队只保留一条
- `oldOpeningAdjustmentBlocksDoubleCounting` — 历史「此前已花」覆盖期内的账目被拒绝，防止重复扣减
- `disabledAlertsDoNotCreateEvents` — 关闭预警后不产生提醒事件
- `refundThenRespendingDoesNotRepeatReminder` — 退款后重新消费不会重复提醒
- `xlsxInlineStringsCanBeImported` — XLSX inlineStr 单元格可正确解析
- `customRuleAppliesToNewCandidate` — 自定义分类规则对新候选生效

### 2.3 Lint

```
0 errors, 12 warnings
```

警告主要是 `MissingApplicationIcon`（未显式设置应用图标）等非阻断项，**不包含 error 级别问题**。

---

## 3. 设备测试代码（已编写，未执行）

设备测试位于 `app\src\androidTest\java\com\jinyuyuliang\budget\BudgetDeviceTest.kt`，
包含 4 个用例：

| 用例 | 断言目标 |
| --- | --- |
| `recordExpenseUpdatesBothAmounts` | 记一笔 20 元后，首页显示「¥ 10.00」今日剩余、动态日均同步更新、预警恰好 1 条 |
| `concurrentDuplicatePersistsOnce` | 6 个并发相同来源写入只落库 1 条，预警 1 条 |
| `badBackupCannotReplaceLedger` | 损坏备份被拒绝且**账本不变** |
| `xlsxParsesOnAndroid` | XLSX 解析在 Android 运行时可用（含 JAXP DOM 在 Android 上的兼容性） |

**这些测试尚未运行过。** 它们需要一台可用的 Android 设备或模拟器。

---

## 4. 模拟器验证失败（环境阻塞，非代码问题）

### 4.1 现象

尝试启动 AVD `BudgetTest`（android-35, x86_64）时，模拟器进程在数秒内退出，`adb devices` 始终为空。

### 4.2 根因诊断（含一次已修正的错误结论）

> **修正说明**：初次诊断曾判断为「QEMU 后端缺少依赖 DLL」，该结论**不成立**，已推翻。
> 下方记录真实原因，供后续排查参考。

**真实原因一：QEMU 后端需要模拟器目录位于 PATH。**

直接调用 QEMU 后端时，若 PATH 中没有模拟器目录，进程会静默秒退、无任何输出：

```
> qemu-system-x86_64-headless.exe --version
→ 无 stdout/stderr，进程立即消失
```

一旦把模拟器目录加入 PATH，同一个二进制立即正常工作：

```powershell
$emu='D:\CodexTasks\android-toolchain\android-sdk\emulator'
$env:PATH="$emu;$emu\lib64;$emu\qemu\windows-x86_64;$env:PATH"
```

```
> qemu-system-x86_64-headless.exe -help
Android Emulator usage: emulator [options] [-qemu args]
  options:
    -list-avds    list available AVDs
    ...
```

补充：该二进制**不支持 `--version`**（会报 `unknown option: --version`），
因此用 `--version` 探测会误判为「损坏」。

**真实原因二（决定性）：x86_64 客户机在无硬件加速时无法执行。**

修正 PATH 后，模拟器**前端与 QEMU 后端都能正常启动**：

```
$ adb devices
emulator-5554   offline
```

进程存活、控制台端口 5554/5555 正常监听，但客户机**一条指令都没有执行**：

```
> 观察 20 秒内 qemu-system-x86_64-headless 的 CPU 时间
cpu before=0.375s after=0.375s delta=0s      ← 完全冻结，非「慢」
```

模拟器自身也明确警告：

```
WARNING | x86_64 emulation may not work without hardware acceleration!
```

而硬件加速**不可用且无法在当前权限下启用**：

```
> emulator.exe -accel-check
6
Android Emulator hypervisor driver is not installed on this machine
```

- `extras/` 目录为空——**没有** AEHD 驱动安装包（`silent_install.bat` 不存在）
- `gvm.sys`、`aehd.sys` 均不存在；`HvHost` 服务为 `Stopped`
- 当前用户 `DESKTOP-KAB4T4F\cxk`，`IsAdmin=False`，**无管理员权限**
- 安装 AEHD 属于安装内核态驱动，必须管理员权限，无法绕过

> 前端日志里的 `Ok: Hypervisor compatibility to run avd: BudgetTest are met`
> 是**误导性的**——它只检查配置，不代表运行时可加速。

### 4.3 结论

**本机无法完成模拟器验证**，这是环境限制，不是应用代码问题。已排除的原因与仍需的条件如下：

| 排查项 | 结论 |
| --- | --- |
| QEMU 后端损坏 | ❌ 已排除，修正 PATH 后正常 |
| QEMU 缺少 DLL | ❌ 已排除（原结论错误） |
| AVD 配置错误 | ❌ 已排除，`hardware-qemu.ini` 路径与参数均正确 |
| 客户机镜像损坏 | ❌ 已排除，内核参数正常组装 |
| **无硬件加速 + 无管理员权限** | ✅ **真实阻塞点** |

补齐该缺口需要管理员权限，选择以下之一：

- 安装 AEHD 驱动（**本机无安装包，需先下载**）
- 启用 WHPX：`DISM /Online /Enable-Feature /FeatureName:HypervisorPlatform /All`
- 或改用物理设备（**推荐，无需管理员权限**）



---

## 5. 未验证项清单（不得记为通过）

以下项目**没有**任何实际运行证据，交付时不应视为已完成：

1. **模拟器全流程操作** — 首次设预算 → 添加预留 → 支出触发预警 → 修改/退款 → 导入重复文件 → 重启 → 备份恢复，全部未在设备上执行。
2. **界面渲染截图** — 首页、键盘弹出、大字体、预警卡片的实际渲染未经截图检查。
3. **`connectedDebugAndroidTest`** — §3 的 4 个设备测试从未运行。
4. **真机安装与启动** — 未在任何物理设备上安装过。
5. **真实付款通知自动记账** — 通知模板未在真实微信/支付宝通知上验证。这是设计上明确保留的「尚未验证」状态：所有通知识别结果一律标记为「需确认」，必须人工核对。
6. **真实平台账单导入** — 解析器只用**合成脱敏样本**验证过。真实微信/支付宝导出账单未测试。
7. **小米 15 / 自报 Android 17 兼容性** — 目标设备未实测。
8. **后台通知限制行为** — 未在真实系统后台策略下验证。

> ⚠️ **不能用模拟器结果代替真机结果，也不能用合成样本通过宣传为真实自动记账成功。**
> 当前真实付款自动记账与小米 15 实测**均未完成**。

---

## 6. 如何补齐验证

### 6.1 用物理设备（推荐，无需管理员权限）

1. 手机开启「开发者选项 → USB 调试」
2. USB 连接电脑，手机上确认「允许 USB 调试」
3. 确认设备可见：

   ```powershell
   D:\CodexTasks\android-toolchain\android-sdk\platform-tools\adb.exe devices -l
   ```

4. 运行设备测试：

   ```powershell
   powershell -ExecutionPolicy Bypass -File D:\CodexTasks\daily-budget-android-dev\scripts\build.ps1 -DeviceTests
   ```

5. 安装并手动走一遍全流程：

   ```powershell
   adb install -r "D:\CodexTasks\daily-budget-android-dev\deliverables\今日余粮-debug.apk"
   ```

### 6.2 修复模拟器

需要管理员权限，选择以下之一：

- 安装 AEHD 驱动并重启
- 启用 WHPX：`DISM /Online /Enable-Feature /FeatureName:HypervisorPlatform /All`
- 修复 QEMU 后端依赖 DLL

---

## 7. 交付物核对

| 项目 | 值 |
| --- | --- |
| APK 路径 | `deliverables\今日余粮-debug.apk` |
| 文件大小 | 59,032,017 字节（约 56.3 MiB） |
| SHA-256 | `9728f6ee6ea2911752d6635b695848a3772beb851884dae4a6dd513f863b9c12` |
| 包名 | `com.jinyuyuliang.budget` |
| 版本 | 0.1.0 (versionCode 1) |
| minSdk / targetSdk | 28 / 36 |
| 应用标签 | 今日余粮 |
| 签名 | debug 签名（**非**应用商店发布包） |

验证 APK 签名：

```powershell
D:\CodexTasks\android-toolchain\android-sdk\build-tools\35.0.0\apksigner.bat verify --print-certs "deliverables\今日余粮-debug.apk"
```

---

## 8. 一句话总结

**构建与纯逻辑测试已验证通过，APK 已产出且校验值已记录；
但所有设备侧验证（模拟器、真机、通知记账、真实账单）均未完成，属明确交付缺口。**
