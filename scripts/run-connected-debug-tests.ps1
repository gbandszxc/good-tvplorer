param(
    [string]$Class,
    [string]$Serial
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$adb = (Get-Command adb -ErrorAction Stop).Source
$deviceArgs = if ($Serial) { @("-s", $Serial) } else { @() }

& (Join-Path $root "gradlew.bat") :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon
if ($LASTEXITCODE -ne 0) {
    throw "Debug 测试 APK 构建失败。"
}

$abiList = (& $adb @deviceArgs shell getprop ro.product.cpu.abilist).Trim().Split(",")
$debugDirectory = Join-Path $root "app\build\outputs\apk\debug"
$appApk = $abiList |
    ForEach-Object { Get-ChildItem -LiteralPath $debugDirectory -Filter "*-$_.apk" -File } |
    Select-Object -First 1
if (-not $appApk) {
    throw "没有与设备 ABI（$($abiList -join ', ')）匹配的 Debug APK。"
}

$testApk = Join-Path $root "app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"
& $adb @deviceArgs install -r -t $appApk.FullName
if ($LASTEXITCODE -ne 0) {
    throw "Debug APK 覆盖安装失败。"
}
& $adb @deviceArgs install -r -t $testApk
if ($LASTEXITCODE -ne 0) {
    throw "测试 APK 覆盖安装失败。"
}

$runnerArgs = @("-w", "-r")
if ($Class) {
    $runnerArgs += @("-e", "class", $Class)
}
$runnerArgs += "com.github.gbandszxc.goodtvplorer.debug.test/androidx.test.runner.AndroidJUnitRunner"

$result = & $adb @deviceArgs shell am instrument @runnerArgs 2>&1
$result | Out-Host
if ($LASTEXITCODE -ne 0 -or $result -match "FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed") {
    throw "Instrumentation 测试失败。"
}
