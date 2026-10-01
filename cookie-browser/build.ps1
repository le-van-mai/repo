<#
  Build Cookie Browser tren Windows bang Android SDK co san, roi (tuy chon) cai va chay tren dien thoai.

  .\build.ps1                 # chi build -> out\CookieBrowser.apk
  .\build.ps1 -Install        # build + adb install
  .\build.ps1 -Install -Run   # build + cai + mo app, neu app crash thi in log loi
#>
param([switch]$Install, [switch]$Run)

# Khong dung 'Stop': PowerShell 5.1 coi stderr cua lenh native (canh bao javac...) la loi.
# Thay vao do kiem tra $LASTEXITCODE sau moi lenh.
$ErrorActionPreference = 'Continue'
$Pkg  = 'com.cookiebrowser.app'
$Root = $PSScriptRoot

function Fail($msg) { Write-Host "LOI: $msg" -ForegroundColor Red; exit 1 }

function Exec([string]$exe, [string[]]$argList, [string]$step) {
    & $exe @argList
    if ($LASTEXITCODE -ne 0) { Fail "$step that bai (exit $LASTEXITCODE)" }
}

# ---- Tim Android SDK ----
$Sdk = $env:ANDROID_HOME
if (-not $Sdk) { $Sdk = $env:ANDROID_SDK_ROOT }
if (-not $Sdk) { $Sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
if (-not (Test-Path $Sdk)) { Fail "Khong tim thay Android SDK tai $Sdk" }

$Bt = Get-ChildItem (Join-Path $Sdk 'build-tools') -Directory -ErrorAction SilentlyContinue |
      Sort-Object { [version](($_.Name -split '-')[0]) } | Select-Object -Last 1
if (-not $Bt) { Fail "Chua co build-tools. Cai bang: sdkmanager `"build-tools;35.0.0`"" }

$Plat = Get-ChildItem (Join-Path $Sdk 'platforms') -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match '^android-\d+$' } |
        Sort-Object { [int]($_.Name -replace 'android-', '') } | Select-Object -Last 1
if (-not $Plat) { Fail "Chua co platform. Cai bang: sdkmanager `"platforms;android-35`"" }

$AndroidJar = Join-Path $Plat.FullName 'android.jar'
$Aapt      = Join-Path $Bt.FullName 'aapt.exe'
$D8        = Join-Path $Bt.FullName 'd8.bat'
$Zipalign  = Join-Path $Bt.FullName 'zipalign.exe'
$Apksigner = Join-Path $Bt.FullName 'apksigner.bat'
foreach ($t in @($Aapt, $D8, $Zipalign, $Apksigner)) { if (-not (Test-Path $t)) { Fail "Thieu $t" } }

# ---- Tim JDK (PATH -> JAVA_HOME -> JDK di kem Android Studio) ----
function Find-JdkTool([string]$name) {
    $c = Get-Command $name -ErrorAction SilentlyContinue
    if ($c) { return $c.Source }
    $dirs = @()
    if ($env:JAVA_HOME) { $dirs += (Join-Path $env:JAVA_HOME 'bin') }
    $dirs += 'C:\Program Files\Android\Android Studio\jbr\bin'
    $dirs += 'C:\Program Files\Android\Android Studio\jre\bin'
    foreach ($d in $dirs) { $p = Join-Path $d "$name.exe"; if (Test-Path $p) { return $p } }
    return $null
}
$Javac   = Find-JdkTool 'javac'
$Keytool = Find-JdkTool 'keytool'
if (-not $Javac -or -not $Keytool) { Fail "Khong tim thay JDK (javac/keytool). Cai JDK 17+ hoac dat JAVA_HOME." }
# d8.bat/apksigner.bat can 'java' trong PATH
$env:PATH = (Split-Path $Javac) + ';' + $env:PATH

Write-Host "SDK: $Sdk | build-tools $($Bt.Name) | $($Plat.Name)"
Write-Host "javac: $Javac"

# ---- Build ----
$Out = Join-Path $Root 'out'
if (Test-Path $Out) { Remove-Item $Out -Recurse -Force }
New-Item -ItemType Directory -Force (Join-Path $Out 'gen'), (Join-Path $Out 'classes') | Out-Null

Exec $Aapt @('package', '-f', '-m', '-J', "$Out\gen", '-M', "$Root\AndroidManifest.xml",
             '-S', "$Root\res", '-I', $AndroidJar, '-F', "$Out\res.ap_",
             '--min-sdk-version', '21', '--target-sdk-version', '34') 'aapt package'

$sources = Get-ChildItem "$Root\src", "$Out\gen" -Recurse -Filter *.java |
           ForEach-Object { '"' + ($_.FullName -replace '\\', '/') + '"' }
[IO.File]::WriteAllLines("$Out\sources.txt", [string[]]$sources)  # UTF-8 khong BOM
Exec $Javac @('-encoding', 'UTF-8', '-source', '8', '-target', '8', '-Xlint:-options',
              '-bootclasspath', $AndroidJar, '-classpath', $AndroidJar,
              '-d', "$Out\classes", "@$Out\sources.txt") 'javac'

$classFiles = Get-ChildItem "$Out\classes" -Recurse -Filter *.class | ForEach-Object { $_.FullName }
Exec $D8 (@('--release', '--min-api', '21', '--lib', $AndroidJar, '--output', $Out) + $classFiles) 'd8'

Copy-Item "$Out\res.ap_" "$Out\unsigned.apk"
Push-Location $Out
& $Aapt add unsigned.apk classes.dex | Out-Null
$code = $LASTEXITCODE
Pop-Location
if ($code -ne 0) { Fail 'aapt add classes.dex that bai' }

Exec $Zipalign @('-f', '4', "$Out\unsigned.apk", "$Out\aligned.apk") 'zipalign'

# Khoa ky co dinh tren may nay -> cac ban build sau cai de len nhau duoc
$Ks = Join-Path $env:USERPROFILE '.android\cookiebrowser.jks'
if (-not (Test-Path $Ks)) {
    New-Item -ItemType Directory -Force (Split-Path $Ks) | Out-Null
    Exec $Keytool @('-genkeypair', '-keystore', $Ks, '-storepass', 'android', '-keypass', 'android',
                    '-alias', 'cb', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000',
                    '-dname', 'CN=Cookie Browser') 'keytool'
}
$Apk = "$Out\CookieBrowser.apk"
Exec $Apksigner @('sign', '--ks', $Ks, '--ks-pass', 'pass:android', '--key-pass', 'pass:android',
                  '--out', $Apk, "$Out\aligned.apk") 'apksigner'
Write-Host "BUILD OK: $Apk" -ForegroundColor Green

if (-not $Install -and -not $Run) { exit 0 }

# ---- Cai dat ----
$Adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $Adb) { $Adb = Join-Path $Sdk 'platform-tools\adb.exe' }
if (-not (Test-Path $Adb)) { Fail "Khong tim thay adb" }
$devices = (& $Adb devices) | Select-String "`tdevice$"
if (-not $devices) { Fail "Khong co dien thoai nao o trang thai 'device' (kiem tra cap USB / USB debugging)" }

if ($Install) {
    $o = (& $Adb install -r $Apk 2>&1 | Out-String)
    Write-Host $o.Trim()
    if ($o -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match') {
        Write-Host "Ban dang cai duoc ky bang khoa khac -> go ban cu roi cai lai (cookie trong app se mat, nap lai tu cookie.txt)" -ForegroundColor Yellow
        & $Adb uninstall $Pkg | Out-Null
        $o = (& $Adb install $Apk 2>&1 | Out-String)
        Write-Host $o.Trim()
    }
    if ($o -notmatch 'Success') { Fail "adb install that bai" }
}

if ($Run) {
    & $Adb logcat -c
    & $Adb shell am force-stop $Pkg
    & $Adb shell monkey -p $Pkg -c android.intent.category.LAUNCHER 1 | Out-Null
    Start-Sleep -Seconds 4
    $appPid = (& $Adb shell pidof $Pkg | Out-String).Trim()
    if ($appPid) {
        Write-Host "App dang chay (pid $appPid)" -ForegroundColor Green
    } else {
        Write-Host "App KHONG chay - co the da crash. Log loi:" -ForegroundColor Red
        & $Adb logcat -d -b crash
        & $Adb logcat -d -s AndroidRuntime:E
        exit 1
    }
}
