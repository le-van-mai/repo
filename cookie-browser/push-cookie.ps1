<#
  Day file cookie sang dien thoai (ghi de /sdcard/Download/cookie.txt) va khoi dong lai Cookie Browser
  de app tu nap cookie moi.

  .\push-cookie.ps1 C:\duong\dan\cookie.txt
#>
param([Parameter(Mandatory = $true)][string]$CookieFile)

$ErrorActionPreference = 'Continue'
$Pkg = 'com.cookiebrowser.app'

if (-not (Test-Path $CookieFile)) { Write-Host "Khong thay file: $CookieFile" -ForegroundColor Red; exit 1 }

$Adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $Adb) { $Adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe' }
if (-not (Test-Path $Adb)) { Write-Host "Khong tim thay adb" -ForegroundColor Red; exit 1 }

& $Adb push $CookieFile /sdcard/Download/cookie.txt
if ($LASTEXITCODE -ne 0) { Write-Host "adb push that bai" -ForegroundColor Red; exit 1 }

& $Adb shell am force-stop $Pkg
& $Adb shell monkey -p $Pkg -c android.intent.category.LAUNCHER 1 | Out-Null
Write-Host "Da day cookie va mo lai app." -ForegroundColor Green
