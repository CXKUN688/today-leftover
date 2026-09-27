# 今日余粮 Android Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付可安装的中文安卓生活费 APK，具有预算、分类记账、预留资金、退款/内部转账、消费预警、通知候选、账单导入和本地备份。

**Architecture:** Kotlin/JVM 纯逻辑模块承担预算、预警及导入规则，Android 模块通过 Room 事务持久化，Compose 展示数据。所有入口统一产生账目变更，统一更新预算与提醒；通知监听只产生可信等级明确的候选记录。

**Tech Stack:** Kotlin、Jetpack Compose、Room、Gradle Kotlin DSL、JUnit、Android 仪器化测试。具体构建版本在 Task 1 根据官方兼容矩阵锁定并写入版本目录。

**Spec:** docs\superpowers\specs\2026-09-26-daily-budget-design.md（包含 3.1 当日预警与动态重算）

## Global Constraints

- 项目目录：<主仓库>。任务文件、工具下载与交付均在 <工作目录> 下，应用强制运行时状态除外。
- 用户设备为小米 15、自报安卓 17；安装及真实通知兼容必须有设备验证证据。
- 第一版为个人本地账本，无需注册。界面为中文、人民币、浅色卡片、大号金额、底部导航。
- 金额统一以整数分保存，所有输入与加减乘除必须检测溢出；剩余天数含今天。
- 预警默认三分之二，选项为 50%、三分之二、80% 或关闭；每日每级最多提醒一次。
- 首批未真机验证的通知模板只产生待确认，不将合成样本通过宣传为真实自动记账成功。
- CSV/XLSX 导入必须预览、去重与事务入账；自己账户转账不算消费，其他转账不能自动作相同假设。
- 核心功能离线；无云同步、广告、分析 SDK、无障碍抓取或支付密码。备份用版本化 JSON。
- 当前仅设计文件已存在；计划中的源码、测试、APK 均尚未产生。计划复核与执行方式选择后才进入产品实施。

## Review Focus

1. 两个来源同时写入同一订单或同一通知更新：只能扣一次，提醒只能发一次（Task 3、5）。
2. 用户下午首次打开应用、后台已接收今日消费：初始额度不可再次扣今天金额（Task 2、3）。
3. 删除或调小已有部分退款的原消费：不得留下退款超过原支出的不一致账本（Task 3）。
4. 用户选择超大/损坏 XLSX 或备份：在有界资源内明确报错，不卡死或覆盖原账本（Task 4、7）。
5. 大字体、键盘与 Android 边到边窗口：金额、保存按钮和预警说明必须可见、可操作（Task 6、8）。

## File Structure

命名空间 `com.jinyuyuliang.budget`，应用模块 `app`，纯逻辑模块 `core`。下列相对路径均相对于项目根目录。

- `settings.gradle.kts`、`build.gradle.kts`、`gradle/libs.versions.toml`、`gradle/wrapper/`：可复现构建；`scripts/build.ps1`：定位 D 盘工具并构建。
- `core/src/main/kotlin/com/jinyuyuliang/budget/model/Models.kt`：金额、账目、预算与候选类型。
- `core/src/main/kotlin/com/jinyuyuliang/budget/budget/BudgetCalculator.kt`、`DailyAllowance.kt`：余额与当日消耗。
- `core/src/main/kotlin/com/jinyuyuliang/budget/alerts/AlertEvaluator.kt`：纯预警规则。
- `core/src/main/kotlin/com/jinyuyuliang/budget/imports/{CsvReader,XlsxReader,BillParser,Deduplicator,Classifier}.kt`：文件解析和候选处理。
- `core/src/main/kotlin/com/jinyuyuliang/budget/notifications/PaymentParser.kt`：通知候选识别。
- `app/src/main/java/com/jinyuyuliang/budget/data/{Entities,BudgetDao,BudgetDatabase,LedgerRepository}.kt`：Room 数据与统一入账。
- `app/src/main/java/com/jinyuyuliang/budget/alerts/AlertDispatcher.kt`：系统提醒投递。
- `app/src/main/java/com/jinyuyuliang/budget/notifications/PaymentListenerService.kt`：通知来源过滤和解析调用。
- `app/src/main/java/com/jinyuyuliang/budget/backup/{BackupCodec,BackupService}.kt`：导出、验证与恢复。
- `app/src/main/java/com/jinyuyuliang/budget/ui/`：`AppNavigation.kt`、`BudgetViewModel.kt`、`HomeScreen.kt`、`EntryScreen.kt`、`LedgerScreen.kt`、`StatisticsScreen.kt`、`BillAssistantScreen.kt`、`SettingsScreen.kt`、`OnboardingScreen.kt`、`Theme.kt`。
- `core/src/test/kotlin/com/jinyuyuliang/budget/`：预算、预警、导入与通知单元测试；`app/src/androidTest/java/com/jinyuyuliang/budget/`：数据库、备份与 UI 测试。
- `docs/BUILD.md`、`docs/USER_GUIDE.md`、`docs/VERIFICATION.md`、`deliverables/`：构建、使用、证据与 APK。

## Task 1: 可构建应用与基础预算算法

**Files:** 构建文件、`scripts/build.ps1`、`Models.kt`、`BudgetCalculator.kt`、`core/.../BudgetCalculatorTest.kt`、最小 `MainActivity.kt` 和 `AndroidManifest.xml`、`docs/BUILD.md`。

**Interfaces:**
- `Transaction(id: String, kind: TransactionKind, amountCents: Long, date: LocalDate, categoryId: String, paymentMethod: String, merchant: String?, note: String?, reservationId: String?, originalTransactionId: String?, addsToBudget: Boolean, source: String, externalId: String?)`；类型包含 EXPENSE、REFUND、INCOME、INTERNAL_TRANSFER。
- `MonthBudget(month: YearMonth, amountCents: Long, openingSpentCents: Long, openingCutoff: LocalDate?, carryoverCents: Long, revision: Long)`；`Reservation(id: String, month: YearMonth, targetCents: Long, name: String, recurring: Boolean)`。
- `BudgetInput(budget: MonthBudget, reservations: List<Reservation>, transactions: List<Transaction>, today: LocalDate)`。
- `BudgetCalculator.calculate(input: BudgetInput): BudgetSummary`，结果含 `availableCents: Long`、`remainingDays: Int`、`dailyCents: Long?`、`todayExpenseCents: Long`；历史月份 dailyCents 为 null。

- [ ] 检查本地工具与官方 Android/Gradle/Kotlin/Compose/Room 兼容矩阵，在 D 盘配置可用工具链，锁定版本与包校验摘要。创建仅用于编译测试的最小 Android/core 模块、Gradle wrapper 和忽略文件；不覆盖任何现有工具。构建脚本设置项目专用 Gradle 用户目录，工具链异常明确失败。
- [ ] 编写 `BudgetCalculatorTest` 失败测试，fixture 是 `BudgetInput` 的命名参数构造器，存于同一测试目录：`assertEquals(3000L, calculate(fixture(budget=30000L, remainingDays=10)).dailyCents)`；加入一笔 2000 分普通消费后断言余额 28000、dailyCents 2800。测试 200000 分预算、50000 分预留支付前后 availableCents 都为 150000；同时覆盖四种月长、闰年、月底、历史月、超支、输入精度和溢出。
- [ ] 运行 `.\gradlew.bat :core:test --tests '*BudgetCalculatorTest'`，确认失败来自缺少预算实现，而非工具配置。
- [ ] 实现数据类型和预算算法；退款、预留、转账、收入与此前已花规则逐条遵循 spec，不用浮点保存金额。
- [ ] 重跑相同测试，执行 `.\gradlew.bat :app:assembleDebug` 验证最小应用可编译，记录实际版本及输出。
- [ ] 仅提交本任务源码、测试、构建配置和 BUILD 说明；工具缓存、SDK 和密钥不提交。

## Task 2: 当日额度与预警纯逻辑

**Files:** `DailyAllowance.kt`、`AlertEvaluator.kt`、`core/.../DailyAllowanceTest.kt`、`core/.../AlertEvaluatorTest.kt`。

**Interfaces:**
- `DailyAllowance.openingCents(input: BudgetInput): Long` 排除今日账目重建首次日基准；`DailyAllowance.usedCents(input: BudgetInput): Long` 计算今天占用普通预算的净支出。
- `AlertSettings(enabled: Boolean, threshold: AlertThreshold)`；threshold 枚举 HALF、TWO_THIRDS、FOUR_FIFTHS。
- `DailyAllowanceSnapshot(date: LocalDate, allowanceCents: Long, revision: Long, highestAlert: AlertLevel?)`；level 枚举 NEAR、REACHED、INSUFFICIENT。
- `AlertEvaluator.evaluate(snapshot: DailyAllowanceSnapshot, usedCents: Long, settings: AlertSettings): AlertDecision?`；decision 包含 level、remainingCents、稳定 `eventId: String`（日期与等级，不含重建次数）。

- [ ] 写失败测试：`assertNull(evaluate(snapshot(3000),1999,twoThirds))`；`assertEquals(1000L,evaluate(snapshot(3000),2000,twoThirds)!!.remainingCents)`；3000 或 3500 分只返回 REACHED。测试同级重复、跨两级、关闭、零/负额度、重建后保留级别、整数溢出边界。
- [ ] 为当日消费写失败测试：预留覆盖的房租不占普通日额度，超出部分计入；今天消费退款抵扣、昨日消费退款不抵今天；午后首次打开仍重建消费前 30 元基准。
- [ ] 运行 `.\gradlew.bat :core:test --tests '*AlertEvaluatorTest' --tests '*DailyAllowanceTest'`，确认红灯。
- [ ] 实现纯逻辑；每次账目变更计算动态余额，但固定参考额度仅跨日或用户修改预算设置时重建。
- [ ] 重跑上述测试，确认 300 元/10 天消费 20 元后的动态 28 元与今日剩余 10 元同时成立，提交本任务。

## Task 3: 统一入账与持久化

**Files:** `data/` 下四个文件、`app/.../LedgerRepositoryTest.kt`、提醒事件数据库表。

**Interfaces:**
- `LedgerMutation` 密封类型：UpsertTransaction、DeleteTransaction、UpdateBudget、UpdateReservation、SetAlertSettings、AdvanceMonth；所有分支使用前述 model 类型。
- `LedgerRepository.apply(mutation: LedgerMutation, today: LocalDate): MutationResult`（suspend），结果包括更新后的 BudgetSummary 和新事件 ID 列表。
- `LedgerRepository.observeMonth(month: YearMonth): Flow<LedgerView>`；LedgerView 包含预算、明细、预留、日基准及当日普通消耗。
- `LedgerRepository.commitCandidates(candidates: List<ImportCandidate>, today: LocalDate): ImportResult`（suspend）；候选类型由 Task 4 定义，先写接口后随 Task 4 完成实现。

- [ ] 写 Room 仪器化失败测试，验证一次 2000 分支出持久化后余额 28000、每日 2800、预警事件恰好一条；重启数据库后值一致。同日并发两次相同来源只写一次；删除已有退款原交易或调低其金额被明确拒绝，先处理退款才能修改。
- [ ] 加入退款关联上限、跨月退款、预留关联删除、月份幂等创建、未来日期拒绝和跨日基准测试。运行 `.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.jinyuyuliang.budget.LedgerRepositoryTest`；须在配置的模拟器上看到预期断言失败，不能把无设备视为红灯。
- [ ] 实现数据库约束、来源唯一键和事务；在同一事务更新基准、账目、已提醒等级与待投递事件。月结转由用户显式选择；调整预算保持已提醒等级。
- [ ] 重跑数据库测试并确认 `:core:test` 仍通过，提交本任务。

## Task 4: 账单解析、分类与去重

**Files:** `imports/` 五个文件、`core/.../BillParserTest.kt`、`DeduplicatorTest.kt`、`core/src/test/resources/bills/`、仓库候选提交实现及测试。

**Interfaces:**
- `ImportCandidate(id: String, transaction: Transaction?, sourceKey: String?, status: CandidateStatus, issue: String?, rowNumber: Int)`，状态 READY、NEEDS_REVIEW、DUPLICATE、INVALID。
- `BillParser.parse(bytes: ByteArray, fileName: String): ParseReport`，结果含 candidates 与整体格式错误；未知结构不得返回假成功。
- `Deduplicator.classify(candidates: List<ImportCandidate>, existing: List<Transaction>, openingCutoff: LocalDate?): List<ImportCandidate>`。
- `Classifier.classify(merchant: String, rules: List<ClassificationRule>): String?`；规则含关键词和分类 ID，未命中返回 null。
- `ImportResult(inserted: Int, skipped: Int, rejected: Int)`；只提交已由用户解决歧义且选中的候选。

- [ ] 用合成脱敏 CSV/XLSX 写失败测试：引号逗号与换行、中文编码、说明行、日期单元格；`assertEquals(1, commitSameFileTwice().totalInserted)`；付款和退款共用订单号仍两条，多次部分退款缺独立退款编号时不可被订单号和类型误合并；同额同秒不同订单仍两条；无编号近似匹配进待确认；此前已花覆盖期进待确认；失败交易行不可自动消费。
- [ ] 为资源上限写测试：文件最多 20 MiB、XLSX 解压总量最多 100 MiB、最多 100000 行，超限明确拒绝；外部实体和外部关系不访问网络。将这些实施防护上限说明于使用文档，便于用户拆分大账单。
- [ ] 运行 `.\gradlew.bat :core:test --tests '*BillParserTest' --tests '*DeduplicatorTest'`，确认预期失败。
- [ ] 实现解析器与事务提交。CSV/XLSX 库需核实 Android 兼容后锁版本；若实现 ZIP/XML 读取，只支持账单所需 sharedStrings、inlineStr、数字与日期，未知结构报错，不宣称通用 Excel 引擎。批量入账结束只评估当前日预警一次。
- [ ] 重跑解析测试及仓库批量回滚/并发导入仪器化测试；记录“合成样本通过”与“真实平台账单未验证”的区别，提交。

## Task 5: 付款通知候选与预警投递

**Files:** `PaymentParser.kt`、`PaymentListenerService.kt`、`AlertDispatcher.kt`、清单权限与服务声明、`core/.../PaymentParserTest.kt`、`app/.../AlertDispatcherTest.kt`。

**Interfaces:**
- `PaymentNotification(packageName: String, notificationKey: String, title: String, text: String, occurredAt: Instant)`。
- `PaymentParser.parse(input: PaymentNotification): ImportCandidate?`；允许微信 `com.tencent.mm`、支付宝 `com.eg.android.AlipayGphone`，普通聊天返回 null。
- `AlertDispatcher.dispatchPending(): Unit`（suspend）；通过 repository 取得未投递事件，固定事件 ID 调用通知 API，已投递事件不重复弹出。

- [ ] 写失败测试：其他包名返回 null，聊天“借我 20 元”返回 null，未验证交易模板必须 NEEDS_REVIEW，缺金额不得入账，相同 notificationKey 的更新不重复扣款。`assertEquals(1, notificationsAfterRetry.size)`；权限拒绝时账目仍保存且应用内提示可读。
- [ ] 运行 `.\gradlew.bat :core:test --tests '*PaymentParserTest'` 和对应 dispatcher 仪器化测试，确认红灯。
- [ ] 实现监听服务、来源白名单、候选保存、模板可信等级和预警通知渠道。接收他人通知权限与发送通知权限分别引导；不要求两者才能使用账本。对普通聊天不保存原文；仅保留必要交易片段。
- [ ] 重跑测试，模拟重复投递与服务重建。Android 通知权限和后台限制按当前官方文档实现，真机未接入时保留“尚未验证”的状态，提交。

## Task 6: 完整手机界面与交互

**Files:** `ui/` 所列文件、`MainActivity.kt`、字符串资源、`app/.../BudgetUiTest.kt`。

**Interfaces:**
- `BudgetViewModel` 提供 `StateFlow<BudgetUiState>` 和 `onAction(action: BudgetAction): Unit`，action 对应入账、编辑删除、筛选、预留/预算编辑、候选确认和权限引导。
- `BudgetUiState` 组合 LedgerView、筛选条件、候选、加载/错误状态与权限状态；所有保存走 repository，不在界面另算余额。

- [ ] 在实现视觉界面前读取 ui-ux-pro-max 技能，围绕既定浅色卡片、大号金额和底部导航选择一致字体/间距/对比度，不新增设计审批问题。
- [ ] 写有意义的 UI 失败测试：首次输入预算后新增 20 元消费，首页同时出现今日剩余 10 元、动态每天 28 元及提醒；删除后汇总恢复。未知候选不可未经确认入账，表单非法金额显示错误且保持输入。
- [ ] 运行 `.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.jinyuyuliang.budget.BudgetUiTest`，确认行为缺失而失败。
- [ ] 实现首次使用、首页、快速记账、月明细和统计、账单助手、分类/规则设置、预留关联、退款、收入计入预算选项和预警设置。区分“今日总支出”“占用普通额度”以解释预留款项；两种每日金额有说明。
- [ ] 测试空状态、错误状态、长数字、2 倍字体、键盘弹出、系统导航条和深色系统环境下的可读性；渲染截图检查，不用源码检查代替界面证据。重跑 UI 测试并提交。

## Task 7: 备份恢复与用户说明

**Files:** `backup/` 两个文件、`SettingsScreen.kt`、备份规则 XML、`app/.../BackupServiceTest.kt`、`docs/USER_GUIDE.md`。

**Interfaces:**
- `BackupCodec.encode(snapshot: BackupSnapshot): ByteArray`；`BackupCodec.decode(bytes: ByteArray): BackupSnapshot`，格式版本 1，解码异常带用户可读原因。
- `BackupService.export(): ByteArray`、`BackupService.preview(bytes: ByteArray): BackupPreview`、`BackupService.restore(bytes: ByteArray): Unit`（suspend）；restore 只在 UI 明确确认整体替换后调用。
- BackupSnapshot 包括 spec 全部持久化类型；恢复保留当日已提醒等级，旧待投递事件不重新发送，防止恢复后轰炸提醒。

- [ ] 写失败测试：导出恢复后账目、预算、预留和去重关联相同；损坏/未知版本/超限/断裂引用拒绝且旧账本不变。`assertEquals(before, databaseAfterInvalidRestore)`。输入最多 20 MiB，超限提示。
- [ ] 运行对应 BackupServiceTest 仪器化测试，确认预期失败。
- [ ] 实现事务恢复、系统文件选择与保存、禁用默认云备份、卸载风险和财务备份文件说明。说明 CSV/XLSX 当前已验证格式范围与导入上限。
- [ ] 重跑备份与核心测试，提交。

## Task 8: 全流程验证、打包和交付

**Files:** `docs/VERIFICATION.md`、`docs/BUILD.md`、`docs/USER_GUIDE.md`、`deliverables/今日余粮-debug.apk`、校验摘要文件。

**Interfaces:** 所有前述任务的公共接口已经集成，无新增业务接口。

- [ ] 完成一次独立代码审查，重点检查五个 Review Focus、预算与预警的两套数字、输入验证、权限拒绝和候选不能绕过确认。修复实质问题并运行相关测试；不为样式调整添加镜像实现的测试。
- [ ] 运行 `.\gradlew.bat :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`，确认退出码 0；在可用模拟器运行 `:app:connectedDebugAndroidTest`。工具或设备不可用时明确列为验证缺口，不记为通过。
- [ ] 安装至模拟器并实际操作：首次设预算 → 添加预留 → 支出触发预警 → 修改/退款 → 导入重复文件 → 重启 → 备份恢复。检查首页、键盘、大字体及预警渲染截图；证据在 D 盘项目内保存。
- [ ] 若用户设备已通过 ADB 明确连接并授权，核对型号/API 后安装并检查启动、权限、后台通知候选。未连接时交付 APK 与操作步骤，明确真实付款自动记账和小米/安卓 17 实测尚未完成；不能用模拟器结果代替。
- [ ] 复制构建产物到 deliverables，记录 SHA-256、应用版本、包名、构建工具版本及安装步骤；第一版个人试用使用已签名 debug APK，明确并非应用商店正式发布包，保留签名材料用于后续同签名升级且不提交密钥。
- [ ] 对照 spec 按条记录实现状态、测试证据及剩余限制，提交代码和文档，向用户提供实际文件链接。仅当 APK 确实生成后声称“可下载 APK”；真实自动记账通过需独立设备证据。

## Plan Self-Review

覆盖映射：spec 1–2 → Tasks 1、6；spec 3、3.1、4 → Tasks 1–3、5；spec 5 → Task 5；spec 6 → Task 4；spec 7 → Tasks 1、3；spec 8 → Task 7；spec 9–10 → Task 8。五个 Review Focus 均已绑定对应测试任务。

执行建议：由当前助手在本会话逐项实现，最后独立审查。模块的预算和入账接口依赖较强，串行实现更易保持一致。用户也可选择分任务子代理实施与逐项审查；尚未选择执行方式，不启动产品实施。
