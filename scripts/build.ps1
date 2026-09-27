param(
    # Android SDK / JDK 所在目录。默认按顺序探测常见位置，也可用 -ToolRoot 或环境变量 DSH_TOOL_ROOT 指定。
    [string]$ToolRoot = $env:DSH_TOOL_ROOT,
    [switch]$DeviceTests
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent

# 依次探测工具链目录：显式参数 > 环境变量 > SDK 所在盘根 > 项目同级 > ANDROID_HOME 推断
function Resolve-ToolRoot {
    if ($ToolRoot -and (Test-Path $ToolRoot)) { return $ToolRoot }
    $candidates = @()
    if ($env:ANDROID_HOME) { $candidates += (Split-Path $env:ANDROID_HOME -Parent) }
    if ($env:ANDROID_SDK_ROOT) { $candidates += (Split-Path $env:ANDROID_SDK_ROOT -Parent) }
    $candidates += (Join-Path (Split-Path $projectRoot -Parent) 'android-toolchain')
    $candidates += (Join-Path $projectRoot 'android-toolchain')
    foreach ($c in $candidates) {
        if ($c -and (Test-Path (Join-Path $c 'android-sdk'))) { return $c }
    }
    return $null
}

$resolved = Resolve-ToolRoot
if (-not $resolved) {
    $msg = @"
找不到 Android 工具链目录。
请用以下任一方式指定：
  .\scripts\build.ps1 -ToolRoot <工具链目录>
  `$env:DSH_TOOL_ROOT = '<工具链目录>'
该目录下应包含 android-sdk\ 与 jdk\ 子目录。
"@
    throw $msg
}
$ToolRoot = $resolved
Write-Host "工具链目录：$ToolRoot"

if (-not $env:JAVA_HOME) {
    $jdk = Get-ChildItem -LiteralPath (Join-Path $ToolRoot 'jdk') -Directory -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if (-not $jdk) { throw "在 $ToolRoot\jdk 下找不到 JDK" }
    $env:JAVA_HOME = $jdk.FullName
}
$env:GRADLE_USER_HOME = Join-Path $ToolRoot 'gradle-cache'
$env:ANDROID_HOME = Join-Path $ToolRoot 'android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME

Push-Location $projectRoot
try {
    & '.\gradlew.bat' :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
    if ($LASTEXITCODE -ne 0) { throw 'Build or verification failed' }
    if ($DeviceTests) {
        & '.\gradlew.bat' :app:connectedDebugAndroidTest
        if ($LASTEXITCODE -ne 0) { throw 'Device tests failed' }
    }
} finally { Pop-Location }
