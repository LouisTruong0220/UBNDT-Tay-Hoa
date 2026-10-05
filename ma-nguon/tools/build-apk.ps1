<#
    Biên dịch app ra file APK — chạy hoàn toàn bằng dòng lệnh, không cần mở Android Studio.

    Cách dùng:
        .\build-apk.ps1              # build bản debug
        .\build-apk.ps1 -Sach        # xoá cache rồi build lại từ đầu

    Lần đầu chạy sẽ lâu (Gradle tải thư viện). Các lần sau nhanh hơn nhiều.
#>
param([switch]$Sach)

$ErrorActionPreference = "Stop"
$Duan = Join-Path (Split-Path -Parent $PSScriptRoot) "android"

function Buoc($n, $t) { Write-Host "`n[$n] $t" -ForegroundColor Cyan }
function Xong($t)     { Write-Host "  OK  $t" -ForegroundColor Green }
function Loi($t)      { Write-Host "  LOI $t" -ForegroundColor Red }

# ── JDK 17 ──────────────────────────────────────────────────
Buoc 1 "Tim JDK 17"
$jdk = Get-ChildItem "C:\Program Files\Microsoft\jdk-17*" -Directory -EA SilentlyContinue |
       Select-Object -First 1
if (-not $jdk) {
    Loi "Chua co JDK 17. Cai bang lenh:  winget install Microsoft.OpenJDK.17"
    exit 1
}
$env:JAVA_HOME = $jdk.FullName
Xong $env:JAVA_HOME

# Vi sao khong dung JDK di kem Android Studio: ban do la OpenJDK 25,
# Gradle 8.7 chi ho tro toi JDK 21 nen se bao loi.

# ── Android SDK ─────────────────────────────────────────────
Buoc 2 "Kiem tra Android SDK"
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
foreach ($c in @("platforms\android-34", "build-tools", "platform-tools")) {
    if (Test-Path "$sdk\$c") { Xong $c } else { Loi "thieu $c" }
}

# local.properties tro toi SDK (Gradle can file nay)
$lp = Join-Path $Duan "local.properties"
$duongDan = $sdk -replace '\\', '\\'
"sdk.dir=$duongDan" | Set-Content $lp -Encoding utf8
Xong "Da ghi local.properties"

# ── Build ───────────────────────────────────────────────────
Buoc 3 "Bien dich"
Push-Location $Duan
try {
    if ($Sach) { & .\gradlew.bat clean --no-daemon }
    & .\gradlew.bat assembleDebug --no-daemon
    $ma = $LASTEXITCODE
} finally { Pop-Location }

# ── Ket qua ─────────────────────────────────────────────────
$apk = Join-Path $Duan "app\build\outputs\apk\debug\app-debug.apk"
if ($ma -eq 0 -and (Test-Path $apk)) {
    $mb = [math]::Round((Get-Item $apk).Length / 1MB, 2)
    Write-Host "`n────────────────────────────────────────" -ForegroundColor Green
    Write-Host " BUILD THANH CONG — $mb MB" -ForegroundColor Green
    Write-Host " $apk"
    Write-Host "────────────────────────────────────────" -ForegroundColor Green
    Write-Host "`nCai len robot:"
    Write-Host "  .\day-len-robot.ps1 -Ip <IP-robot> -CaiApp"
} else {
    Write-Host "`nBUILD HONG (ma loi $ma)" -ForegroundColor Red
    Write-Host "Doc thong bao loi o tren. Cac loi hay gap:"
    Write-Host "  - 'Unsupported class file major version' -> sai phien ban JDK"
    Write-Host "  - 'Failed to resolve com.github.orionagent' -> mang chan jitpack.io"
    Write-Host "  - 'SDK location not found' -> thieu Android SDK, xem buoc 2"
    exit 1
}
