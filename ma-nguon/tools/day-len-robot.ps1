<#
    Đẩy media và cài app lên robot Nova.

    Cách dùng:
        .\day-len-robot.ps1                             # cáp USB — tự nhận, không cần IP
        .\day-len-robot.ps1 -CaiApp                     # đẩy media + cài APK
        .\day-len-robot.ps1 -Ip 192.168.1.50 -CaiApp    # qua Wi-Fi
        .\day-len-robot.ps1 -ChiKiemTra                 # chỉ xem trên máy đang có gì

    Robot phải BẬT "Gỡ lỗi lâu dài" và đã khởi động lại thì adb mới thấy.

    ⚠ Phải dùng adb của Android SDK (1.0.41). Bản C:\Windows\adb.exe là adb 1.0.39
      của PUDU, lệch phiên bản với adb server nên hai bên đá nhau liên tục.
#>
param(
    [string]$Ip,
    [switch]$Usb,
    [switch]$CaiApp,
    [switch]$ChiKiemTra
)

$ErrorActionPreference = "Stop"
$Goc      = Split-Path -Parent $PSScriptRoot
$Media    = Join-Path $Goc "media-robot"
$Apk      = Join-Path $Goc "android\app\build\outputs\apk\debug\app-debug.apk"
$ThuMucRb = "/sdcard/roboworld-letan"
$AdbExe   = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $AdbExe)) { $AdbExe = "C:\Windows\adb.exe" }

function Adb { & $AdbExe @args }

function Buoc($n, $chu) { Write-Host "`n[$n] $chu" -ForegroundColor Cyan }
function Loi($chu)      { Write-Host "  ✗ $chu" -ForegroundColor Red }
function Xong($chu)     { Write-Host "  ✓ $chu" -ForegroundColor Green }

# ── 1. Kết nối ────────────────────────────────────────────────
Buoc 1 "Kết nối robot"
if ($Ip) {
    Adb connect "${Ip}:5555" | Write-Host
} else {
    Write-Host "  Dùng cáp USB (cắm vào cổng ở PHẦN ĐẦU robot)"
}

$ds = (Adb devices) -split "`n" | Where-Object { $_ -match "\sdevice$" }
if (-not $ds) {
    Loi "Không thấy robot nào."
    Write-Host @"
  Kiểm tra lại:
   - Robot đã bật 'Gỡ lỗi lâu dài' và ĐÃ KHỞI ĐỘNG LẠI chưa?
     (chỉ bật 'Bật gỡ lỗi' thôi thì mất sau khi reboot)
   - Máy tính và robot có cùng mạng Wi-Fi không?
   - Địa chỉ IP có đúng không? Xem trong Cài đặt của robot.
"@ -ForegroundColor Yellow
    exit 1
}
Xong "Đã kết nối: $($ds -join ', ')"

# ── 2. Xem trên robot đang có gì ──────────────────────────────
Buoc 2 "Kiểm tra dữ liệu đang có trên robot"
$dangCo = Adb shell "ls $ThuMucRb 2>/dev/null | wc -l"
Write-Host "  Thư mục $ThuMucRb : $($dangCo.Trim()) mục"
$rom = (Adb shell "getprop ro.build.display.id").Trim()
$sn  = (Adb shell "getprop ro.serialno").Trim()
Write-Host "  SN máy  : $sn"
Write-Host "  Bản ROM : $rom"

if ($ChiKiemTra) {
    Buoc "-" "Danh sách file media trên robot"
    Adb shell "ls -R $ThuMucRb 2>/dev/null" | Write-Host
    exit 0
}

# ── 3. Đẩy media ──────────────────────────────────────────────
Buoc 3 "Đẩy ảnh và video lên robot"
if (-not (Test-Path $Media)) {
    Loi "Chưa có thư mục media-robot. Chạy trước:  python tools\chuan-bi-media.py"
    exit 1
}
$soFile = (Get-ChildItem $Media -Recurse -File).Count
$dungLuong = [math]::Round((Get-ChildItem $Media -Recurse -File | Measure-Object Length -Sum).Sum / 1MB, 1)
Write-Host "  Chuẩn bị đẩy $soFile file ($dungLuong MB) — mất vài phút, đừng tắt cửa sổ này."

Adb shell "mkdir -p $ThuMucRb"
Adb push "$Media\." $ThuMucRb | Select-Object -Last 3 | Write-Host

$sauKhiDay = (Adb shell "find $ThuMucRb -type f | wc -l").Trim()
if ([int]$sauKhiDay -ge $soFile) {
    Xong "Đã lên đủ $sauKhiDay file"
} else {
    Loi "Chỉ thấy $sauKhiDay / $soFile file trên robot — đẩy lại lần nữa"
}

# ── 4. Cài APK ────────────────────────────────────────────────
if ($CaiApp) {
    Buoc 4 "Cài app"
    if (-not (Test-Path $Apk)) {
        Loi "Chưa có file APK."
        Write-Host @"
  Phải build trước bằng Android Studio:
    1. Mở thư mục  $Goc\android
    2. Chờ Gradle sync xong
    3. Menu Build → Build Bundle(s)/APK(s) → Build APK(s)
  APK sẽ nằm ở: android\app\build\outputs\apk\debug\app-debug.apk
"@ -ForegroundColor Yellow
        exit 1
    }
    Adb install -r $Apk | Write-Host
    Xong "Đã cài xong"
}

# ── 5. Nhắc việc cuối ─────────────────────────────────────────
Write-Host "`n────────────────────────────────────────────" -ForegroundColor DarkGray
Write-Host "VIỆC CUỐI — làm trên màn hình robot:" -ForegroundColor Yellow
Write-Host "  Mở app bằng cách bấm biểu tượng trên RobotOS Home."
Write-Host "  KHÔNG mở bằng nút Run của Android Studio — app sẽ không được"
Write-Host "  gầm máy uỷ quyền, bấm dẫn đường sẽ không có phản ứng gì."
Write-Host "────────────────────────────────────────────`n" -ForegroundColor DarkGray
