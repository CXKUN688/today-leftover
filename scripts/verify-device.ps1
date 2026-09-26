# 今日余粮 — 设备验证脚本
#
# 用途：连接真机（或可用的模拟器）后，一键完成安装与设备测试，并留存证据。
# 用法：
#   powershell -ExecutionPolicy Bypass -File scripts\verify-device.ps1
#   powershell -ExecutionPolicy Bypass -File scripts\verify-device.ps1 -SkipTests
#
# 产物输出到 evidence\ 目录：
#   device-info.txt        设备型号 / API / 分辨率
#   connected-tests.txt    connectedDebugAndroidTest 结果
#   install.txt            安装结果
#   screenshots\*.png      手动操作截图（需自行补充）
#   logcat-app.txt         应用相关日志

param(
    [switch]$SkipTests,
    [switch]$SkipInstall
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$toolRoot    = 'D:\CodexTasks\android-toolchain'
$adb         = Join-Path $toolRoot 'android-sdk\platform-tools\adb.exe'
$evidence    = Join-Path $projectRoot 'evidence'
$apk         = Join-Path $projectRoot 'deliverables\今日余粮-debug.apk'

New-Item -ItemType Directory -Force -Path $evidence | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $evidence 'screenshots') | Out-Null

function Section($text) { Write-Host "`n=== $text ===" -ForegroundColor Cyan }
function Fail($text) { Write-Host "失败：$text" -ForegroundColor Red; exit 1 }

# ---------- 1. 环境与设备 ----------
Section '环境检查'

if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = (Get-ChildItem -LiteralPath (Join-Path $toolRoot 'jdk') -Directory | Select-Object -First 1).FullName
}
$env:GRADLE_USER_HOME = Join-Path $toolRoot 'gradle-cache'
$env:ANDROID_HOME     = Join-Path $toolRoot 'android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME

if (-not (Test-Path $adb)) { Fail "找不到 adb：$adb" }
if (-not (Test-Path $apk)) { Fail "找不到 APK：$apk（请先运行 scripts\build.ps1）" }

& $adb start-server | Out-Null
$devices = & $adb devices | Select-String -Pattern '^\S+\s+(device|offline|unauthorized)$'

if (-not $devices) {
    Write-Host @'
未检测到设备。请依次确认：
  1. 手机「设置 → 关于手机」连点版本号，开启开发者选项
  2. 「开发者选项 → USB 调试」已开启
  3. 数据线已连接，且手机上点了「允许 USB 调试」
  4. 若弹出授权框，勾选「一律允许」
排查命令：
  adb devices -l
  Get-PnpDevice -PresentOnly | Where-Object { $_.InstanceId -like 'USB\*' }
'@ -ForegroundColor Yellow
    Fail '无可用设备'
}

$serial = ($devices[0] -split '\s+')[0]
Write-Host "使用设备：$serial"

# ---------- 2. 记录设备信息 ----------
Section '记录设备信息'
$info = & $adb -s $serial shell getprop 2>&1
$model   = ($info | Select-String '\[ro.product.model\]'   | ForEach-Object { $_ -replace '.*\[(.*)\].*','$1' })
$brand   = ($info | Select-String '\[ro.product.brand\]'   | ForEach-Object { $_ -replace '.*\[(.*)\].*','$1' })
$android = ($info | Select-String '\[ro.build.version.release\]' | ForEach-Object { $_ -replace '.*\[(.*)\].*','$1' })
$sdk     = ($info | Select-String '\[ro.build.version.sdk\]'     | ForEach-Object { $_ -replace '.*\[(.*)\].*','$1' })
$abi     = ($info | Select-String '\[ro.product.cpu.abi\]'       | ForEach-Object { $_ -replace '.*\[(.*)\].*','$1' })
$size    = (& $adb -s $serial shell wm size 2>&1) -join ' '
$density = (& $adb -s $serial shell wm density 2>&1) -join ' '

$deviceInfo = @"
今日余粮 — 设备验证信息
========================
验证时间       : $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')
设备序列号     : $serial
品牌           : $brand
型号           : $model
Android 版本   : $android  (API $sdk)
CPU ABI        : $abi
屏幕           : $size
密度           : $density

APK            : $apk
APK SHA-256    : $((Get-FileHash $apk -Algorithm SHA256).Hash)
"@
$deviceInfo | Set-Content (Join-Path $evidence 'device-info.txt') -Encoding UTF8
$deviceInfo
Write-Host "已保存：evidence\device-info.txt"

# ---------- 3. 安装 ----------
if (-not $SkipInstall) {
    Section '安装 APK'
    $installLog = & $adb -s $serial install -r $apk 2>&1
    $installLog | Tee-Object -FilePath (Join-Path $evidence 'install.txt')
    if ($installLog -match 'Failure') { Fail "安装失败：$installLog" }
    Write-Host "安装成功"
}

# ---------- 4. 冒烟测试 ----------
Section '启动冒烟测试'
& $adb -s $serial shell am force-stop com.jinyuyuliang.budget | Out-Null
& $adb -s $serial shell monkey -p com.jinyuyuliang.budget -c android.intent.category.LAUNCHER 1 2>&1 |
    Set-Content (Join-Path $evidence 'launch.txt') -Encoding UTF8
Start-Sleep 6

$resumed = (& $adb -s $serial shell dumpsys activity activities 2>&1 |
    Select-String 'mResumedActivity|ResumedActivity') -join "`n"
$resumed | Set-Content (Join-Path $evidence 'resumed-activity.txt') -Encoding UTF8
if ($resumed -notmatch 'com.jinyuyuliang.budget') {
    Write-Host "警告：应用似乎未处于前台，请检查截图" -ForegroundColor Yellow
} else {
    Write-Host "应用已启动并位于前台"
}

& $adb -s $serial exec-out screencap -p > (Join-Path $evidence 'screenshots\01-启动首页.png')
Write-Host "已保存：evidence\screenshots\01-启动首页.png"

# ---------- 5. 设备测试 ----------
if (-not $SkipTests) {
    Section '运行设备测试（connectedDebugAndroidTest）'
    Push-Location $projectRoot
    try {
        & '.\gradlew.bat' :app:connectedDebugAndroidTest --console=plain 2>&1 |
            Tee-Object -FilePath (Join-Path $evidence 'connected-tests.txt')
        $code = $LASTEXITCODE
    } finally { Pop-Location }

    if ($code -ne 0) {
        Write-Host "设备测试未全部通过（退出码 $code），详见 evidence\connected-tests.txt" -ForegroundColor Red
    } else {
        Write-Host "设备测试通过" -ForegroundColor Green
    }

    # 汇总测试结果 XML
    $xmlDir = Join-Path $projectRoot 'app\build\outputs\androidTest-results\connected'
    if (Test-Path $xmlDir) {
        Get-ChildItem -Recurse $xmlDir -Filter '*.xml' | ForEach-Object {
            $x = [xml](Get-Content $_.FullName)
            "{0}: tests={1} failures={2} errors={3} skipped={4}" -f `
                $_.Name, $x.testsuite.tests, $x.testsuite.failures, $x.testsuite.errors, $x.testsuite.skipped
        } | Tee-Object -FilePath (Join-Path $evidence 'test-summary.txt')
    }
}

# ---------- 6. 日志 ----------
Section '收集应用日志'
& $adb -s $serial logcat -d -v time 2>&1 |
    Select-String -Pattern 'jinyuyuliang|AndroidRuntime|FATAL' |
    Select-Object -Last 300 |
    Set-Content (Join-Path $evidence 'logcat-app.txt') -Encoding UTF8
Write-Host "已保存：evidence\logcat-app.txt"

# ---------- 7. 手动验证清单 ----------
Section '下一步：手动验证'
Write-Host @'
设备测试只覆盖了 4 个自动化用例，以下流程需要你手动操作并截图：

  1. 首次设置预算         → 截图：设置本月生活费
  2. 首页金额显示         → 截图：今日剩余 + 动态每天可花（两个数字）
  3. 添加预留（如房租）   → 截图：预留设置后首页
  4. 记一笔支出触发预警   → 截图：预警卡片
  5. 编辑 / 退款          → 截图：退款后的账目
  6. 导入同一账单两次     → 截图：第二次显示重复未入账
  7. 导入真实微信/支付宝账单 → 截图：预览与入账结果
  8. 杀进程重启           → 截图：数据仍在
  9. 导出备份 → 恢复      → 截图：恢复确认对话框
 10. 系统字体调至 2 倍    → 截图：金额与按钮仍可见可点
 11. 付款通知自动识别     → 截图：候选记录标记「需确认」

截图保存到 evidence\screenshots\，命名如 02-设置预算.png

导出截图命令示例：
  adb exec-out screencap -p > evidence\screenshots\02-设置预算.png

完成后把 evidence\ 目录发回，即可补全 docs\VERIFICATION.md。
'@
