param([switch]$DeviceTests)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$toolRoot='D:\CodexTasks\android-toolchain'
if(-not $env:JAVA_HOME){$env:JAVA_HOME=(Get-ChildItem -LiteralPath (Join-Path $toolRoot 'jdk') -Directory | Select-Object -First 1).FullName}
$env:GRADLE_USER_HOME=Join-Path $toolRoot 'gradle-cache'
$env:ANDROID_HOME=Join-Path $toolRoot 'android-sdk'
Push-Location $projectRoot
try { & '.\gradlew.bat' :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug; if($LASTEXITCODE -ne 0){throw 'Build or verification failed'};if($DeviceTests){ & '.\gradlew.bat' :app:connectedDebugAndroidTest;if($LASTEXITCODE -ne 0){throw 'Device tests failed'}} } finally {Pop-Location}
