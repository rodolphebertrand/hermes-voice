# build-hermes-voice.ps1 ? build APK Hermes Voice sur le PC (PowerShell 5.1+, encodage ASCII)
$ErrorActionPreference = "Stop"

$workDir = "D:\hermes-voice"
# Public repo: HTTPS clone/pull needs no key. Push happens from the .142 server over SSH.
$repo   = "https://github.com/rodolphebertrand/hermes-voice.git"
$branch = "hermes-voice"

# JDK 17
$jdks = @("C:\Program Files\Eclipse Adoptium", "C:\Program Files\Java") |
        ForEach-Object { Get-ChildItem $_ -Directory -EA SilentlyContinue } |
        Where-Object { $_.Name -match "jdk-17" } | Select-Object -First 1
if (-not $jdks) { throw "JDK 17 not found. Install Eclipse Temurin JDK 17 (adoptium.net) and rerun." }
$env:JAVA_HOME = $jdks.FullName

# Android SDK
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
if (-not (Test-Path $env:ANDROID_HOME)) { throw "Android SDK not found at $env:ANDROID_HOME" }
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\cmdline-tools\latest\bin;$env:PATH"

Write-Host "JAVA_HOME=$env:JAVA_HOME"
Write-Host "ANDROID_HOME=$env:ANDROID_HOME"

# SDK components required by compileSdk=37 (cmdline-tools absent sur ce PC -> on verifie juste)
if (Test-Path "$env:ANDROID_HOME\cmdline-tools") {
    sdkmanager --licenses | Out-Null
    sdkmanager "platforms;android-37.0" "build-tools;37.0.0" "platform-tools" | Out-Host
} else {
    Write-Host "cmdline-tools absent: on suppose platforms/build-tools deja presents"
    if (-not (Test-Path "$env:ANDROID_HOME\platforms\android-37.0")) { Write-Host "ATTENTION: platforms\android-37.0 manquant" }
}

# Clone or pull
if (Test-Path "$workDir\.git") {
    Set-Location $workDir
    git fetch origin
    git checkout $branch
    git pull --ff-only origin $branch
} else {
    git clone -b $branch $repo $workDir
    Set-Location $workDir
}
Write-Host "HEAD: $(git rev-parse --short HEAD) - $(git log -1 --format=%s)"

# Build
.\gradlew :app:assembleSideloadDebug --console=plain

# Locate APK
$apk = Get-ChildItem "app\build\outputs\apk\sideload\debug\*.apk" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $apk) { throw "Build finished but no APK found" }
$mo = [math]::Round($apk.Length/1MB,1)
Write-Host ""
Write-Host "APK READY: $($apk.FullName) ($mo MB)"
Write-Host "Install on S21 FE:"
Write-Host "  adb -s 192.168.3.4:5555 install -r `"$($apk.FullName)`""