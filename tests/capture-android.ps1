[CmdletBinding()]
param(
  [Parameter(Mandatory)][string] $FixturePath,
  [Parameter(Mandatory)][string] $ApkPath,
  [string] $Adb = (Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'),
  [string] $Device = 'emulator-5556',
  [string] $OutputDirectory = 'android/app/build/role-screens'
)

$ErrorActionPreference = 'Stop'
$fixture = Get-Content -LiteralPath $FixturePath -Raw -Encoding UTF8 | ConvertFrom-Json
if ((& $Adb -s $Device shell getprop ro.kernel.qemu).Trim() -ne '1') {
  throw 'Screenshots require an isolated Android emulator.'
}
New-Item -ItemType Directory -Force $OutputDirectory | Out-Null

function Read-Ui {
  & $Adb -s $Device shell uiautomator dump /sdcard/muaythai-capture.xml | Out-Null
  if ($LASTEXITCODE -ne 0) { throw 'Unable to inspect Android UI.' }
  [xml]$xml = (& $Adb -s $Device exec-out cat /sdcard/muaythai-capture.xml) -join ''
  return $xml
}

function Tap-Node($Node) {
  if (!$Node -or $Node.bounds -notmatch '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') {
    throw 'Requested Android element is unavailable.'
  }
  & $Adb -s $Device shell input tap ([int](([int]$Matches[1]+[int]$Matches[3])/2)) ([int](([int]$Matches[2]+[int]$Matches[4])/2))
}

function Tap-Text([string] $Text) {
  $xml = Read-Ui
  Tap-Node ($xml.SelectSingleNode("//node[@text='$Text' or @content-desc='$Text']"))
}

function Enter-Field([string] $Id, [string] $Value) {
  $xml = Read-Ui
  Tap-Node ($xml.SelectSingleNode("//node[@resource-id='vn.muaythai.app:id/$Id']"))
  Start-Sleep -Milliseconds 600
  & $Adb -s $Device shell input keycombination 113 29
  & $Adb -s $Device shell input keyevent 67
  & $Adb -s $Device shell input text $Value
  & $Adb -s $Device shell input keyevent 4
}

function Expect-Text([string] $Text) {
  for ($attempt=0; $attempt -lt 6; $attempt++) {
    $xml = Read-Ui
    if ($xml.SelectSingleNode("//node[@text='$Text' or @content-desc='$Text']")) { return }
    Start-Sleep -Seconds 1
  }
  throw "Expected Android text is missing: $Text"
}

function Capture([string] $Role, [string] $Name) {
  Start-Sleep -Milliseconds 1200
  $folder = Join-Path $OutputDirectory $Role.ToLowerInvariant()
  New-Item -ItemType Directory -Force $folder | Out-Null
  & $Adb -s $Device shell screencap -p /data/local/tmp/muaythai-capture.png
  & $Adb -s $Device pull /data/local/tmp/muaythai-capture.png (Join-Path $folder ($Name+'.png')) | Out-Null
  if ($LASTEXITCODE -ne 0) { throw 'Screenshot transfer failed.' }
  "Captured $Role/$Name"
}

& $Adb -s $Device install -r $ApkPath
if ($LASTEXITCODE -ne 0) { throw 'Could not install QA APK.' }
& $Adb -s $Device shell input keyevent 224
& $Adb -s $Device shell wm dismiss-keyguard
& $Adb -s $Device shell settings put system accelerometer_rotation 0
& $Adb -s $Device shell settings put system user_rotation 0
& $Adb -s $Device shell mkdir -p /sdcard/Pictures
$photo = Join-Path (Split-Path -Parent $FixturePath) 'course.jpg'
& $Adb -s $Device push $photo /sdcard/Pictures/muaythai-course.jpg | Out-Null
& $Adb -s $Device shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Pictures/muaythai-course.jpg | Out-Null

$tabs = @{
  ADMIN = @('Quét','Lịch','Học viên','Quản lý')
  RECEPTION = @('Quét','Lịch','Học viên')
  TRAINER = @('Lịch dạy','Yêu cầu','Học viên PT')
  LEARNER = @('Lịch','Đặt lịch','Thẻ QR','Hành trình')
}
$firstTitle = @{ ADMIN='Quét QR check-in'; RECEPTION='Quét QR check-in'; TRAINER='Lịch dạy của tôi'; LEARNER='Lịch của tôi' }
$names = @{
  ADMIN=@('scan','calendar','learners','management')
  RECEPTION=@('scan','calendar','learners')
  TRAINER=@('teaching-calendar','requests','pt-learners')
  LEARNER=@('calendar','booking','qr-cards','journey')
}

foreach ($role in @('ADMIN','RECEPTION','TRAINER','LEARNER')) {
  $account = $fixture.accounts.$role
  & $Adb -s $Device shell am force-stop vn.muaythai.app
  & $Adb -s $Device shell pm clear vn.muaythai.app | Out-Null
  & $Adb -s $Device shell pm grant vn.muaythai.app android.permission.CAMERA
  & $Adb -s $Device shell am start -W -n vn.muaythai.app/.LoginActivity | Out-Null
  Expect-Text 'Đăng nhập'
  if ($role -eq 'ADMIN') { Capture $role 'login' }
  Enter-Field email $account.username
  Enter-Field password $account.password
  Tap-Text 'Đăng nhập'
  Expect-Text $firstTitle[$role]
  $index = 0
  foreach ($tab in $tabs[$role]) {
    $xml = Read-Ui
    $node = $xml.SelectSingleNode("//node[@resource-id='vn.muaythai.app:id/workspaceNav']//node[@text='$tab']")
    if (!$node) { $node = $xml.SelectSingleNode("//node[@text='$tab']") }
    Tap-Node $node
    Capture $role $names[$role][$index]
    $index++
  }
  $xml = Read-Ui
  Tap-Node ($xml.SelectSingleNode("//node[@resource-id='vn.muaythai.app:id/accounts']"))
  Expect-Text 'Tài khoản của tôi'
  Capture $role 'profile'
  Tap-Text 'Chọn ảnh'
  Start-Sleep -Seconds 2
  $xml = Read-Ui
  $photoNode = $xml.SelectSingleNode("//node[contains(@content-desc,'muaythai-course') or @resource-id='com.google.android.providers.media.module:id/icon_thumbnail']")
  if (!$photoNode) {
    $screen = (& $Adb -s $Device shell wm size) -join ''
    if ($screen -notmatch '(\d+)x(\d+)') { throw 'Could not read emulator screen size.' }
    $width = [int]$Matches[1]
    $photoNode = $xml.SelectNodes("//node[@clickable='true']") | Where-Object {
      if ($_.bounds -match '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') {
        $cellWidth = [int]$Matches[3]-[int]$Matches[1]
        $cellHeight = [int]$Matches[4]-[int]$Matches[2]
        [int]$Matches[1] -eq 0 -and $cellWidth -gt $width/4 -and $cellWidth -lt $width/2 -and $cellHeight -gt $width/4
      }
    } | Select-Object -First 1
  }
  if ($photoNode) {
    Tap-Node $photoNode
    $pickerUi = Read-Ui
    $done = $pickerUi.SelectSingleNode("//node[@text='Done']")
    if ($done) { Tap-Node $done }
    Expect-Text 'Lưu ảnh'
    Capture $role 'profile-image-preview'
    Tap-Text 'Lưu ảnh'
    Expect-Text 'Tài khoản của tôi'
    Capture $role 'profile-image-saved'
  } else {
    & $Adb -s $Device shell input keyevent 4
    throw "Photo picker image was not found for $role."
  }
  "PASS Android screens and image upload $role"
}
