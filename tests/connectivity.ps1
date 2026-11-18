[CmdletBinding()]
param(
  [Parameter(Mandatory)][string] $ApkPath,
  [switch] $SkipInstall,
  [string] $Device = 'emulator-5556',
  [string] $Adb = (Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'),
  [string] $OutputDirectory = 'android/app/build/network-qa'
)
$ErrorActionPreference='Stop'
if ((& $Adb -s $Device shell getprop ro.kernel.qemu).Trim() -ne '1') {
  throw 'Connectivity tests require an isolated emulator.'
}
New-Item -ItemType Directory -Force $OutputDirectory | Out-Null
$wifi = (& $Adb -s $Device shell settings get global wifi_on).Trim()
$data = (& $Adb -s $Device shell settings get global mobile_data).Trim()
function Ui {
  for($attempt=0;$attempt -lt 3;$attempt++) {
    & $Adb -s $Device shell rm -f /sdcard/network-test.xml
    $dump=(& $Adb -s $Device shell uiautomator dump /sdcard/network-test.xml) -join ''
    if($dump -like '*dumped to*') {
      [xml]$xml=(& $Adb -s $Device exec-out cat /sdcard/network-test.xml) -join ''
      return $xml
    }
    Start-Sleep -Milliseconds 300
  }
  throw 'Could not read fresh Android UI state'
}
function Tap($node) {
  if (!$node -or $node.bounds -notmatch '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') {throw 'UI element unavailable'}
  & $Adb -s $Device shell input tap ([int](([int]$Matches[1]+[int]$Matches[3])/2)) ([int](([int]$Matches[2]+[int]$Matches[4])/2))
}
function Package([string]$name) {
  for($i=0;$i -lt 8;$i++) {
    $xml=Ui
    if($xml.SelectSingleNode("//node[@package='$name']")){return $xml}
    Start-Sleep -Milliseconds 300
  }
  throw "Android package not visible: $name"
}
function Offline([bool]$expected) {
  for($i=0;$i -lt 12;$i++) {
    $xml=Ui
    $visible=$null -ne $xml.SelectSingleNode("//node[starts-with(@text,'Chưa có kết nối Internet.')]")
    if($visible -eq $expected){return $xml}
    Start-Sleep -Milliseconds 500
  }
  throw "Offline notice expected=$expected actual=$visible"
}
function Network([bool]$enabled) {
  $mode=if($enabled){'enable'}else{'disable'}
  & $Adb -s $Device shell svc wifi $mode
  & $Adb -s $Device shell svc data $mode
}
function Capture([string]$name) {
  & $Adb -s $Device shell screencap -p /data/local/tmp/network-test.png
  & $Adb -s $Device pull /data/local/tmp/network-test.png (Join-Path $OutputDirectory ($name+'.png')) | Out-Null
}
function Enter([string]$id,[string]$value) {
  $xml=Ui
  Tap ($xml.SelectSingleNode("//node[@resource-id='vn.muaythai.app:id/$id']"))
  Start-Sleep -Milliseconds 600
  & $Adb -s $Device shell input text x
  Start-Sleep -Milliseconds 300
  & $Adb -s $Device shell input keycombination 113 29
  & $Adb -s $Device shell input keyevent 67
  & $Adb -s $Device shell input text $value
  & $Adb -s $Device shell input keyevent 4
}
try {
  & $Adb -s $Device shell am force-stop vn.muaythai.app
  if(!$SkipInstall) {
    & $Adb -s $Device install -r $ApkPath | Out-Null
    if($LASTEXITCODE -ne 0){throw 'APK install failed'}
  }
  & $Adb -s $Device shell pm clear vn.muaythai.app | Out-Null
  Start-Sleep -Seconds 2
  & $Adb -s $Device shell input keyevent 224
  & $Adb -s $Device shell wm dismiss-keyguard
  Network $false
  & $Adb -s $Device shell am start -W -n vn.muaythai.app/.LoginActivity
  if($LASTEXITCODE -ne 0){throw 'App launch failed'}
  'CASE cold start offline'
  $null=Offline $true
  Capture 'login-offline'
  Enter 'email' 'network-check@example.test'
  $draft=(Ui).SelectSingleNode("//node[@resource-id='vn.muaythai.app:id/email']").text
  Network $true
  $xml=Offline $false
  if($xml.SelectSingleNode("//node[@resource-id='vn.muaythai.app:id/email']").text -ne $draft){throw 'Login draft lost'}
  'PASS cold start offline and login draft survives reconnection'
  Capture 'login-online'
  $line=[IO.File]::ReadAllLines((Join-Path (Get-Location) 'deploy/.env')) | Where-Object {$_.StartsWith('DEMO_ADMIN_PASSWORD=')}
  Enter 'email' 'admin@example.test'
  Enter 'password' $line.Substring($line.IndexOf('=')+1)
  $xml=Ui
  Tap ($xml.SelectSingleNode("//node[@text='Đăng nhập']"))
  Start-Sleep -Seconds 2
  $xml=Ui
  if(!$xml.SelectSingleNode("//node[@resource-id='vn.muaythai.app:id/workspaceNav']")){throw 'Workspace unavailable'}
  Network $false
  'CASE workspace offline'
  $xml=Offline $true
  Capture 'workspace-offline'
  Tap ($xml.SelectSingleNode("//node[@text='Cài đặt mạng']"))
  $null=Package 'com.android.settings'
  for($back=0;$back -lt 4;$back++) {
    & $Adb -s $Device shell input keyevent 4
    $xml=Ui
    if($xml.SelectSingleNode("//node[@package='vn.muaythai.app']")){break}
  }
  $null=Package 'vn.muaythai.app'
  'CASE return from settings offline'
  $null=Offline $true
  Network $true
  $xml=Offline $false
  if(!$xml.SelectSingleNode("//node[@resource-id='vn.muaythai.app:id/workspaceNav']")){throw 'Session lost on reconnect'}
  'PASS workspace offline, settings action and reconnect preserve session'
  Capture 'workspace-online'
  & $Adb -s $Device shell input keyevent 3
  Network $false
  & $Adb -s $Device shell input keyevent 187
  $xml=Ui
  Tap ($xml.SelectSingleNode("//node[@content-desc='MuayThai & Fitness Center']"))
  'CASE return from recent task offline'
  $null=Offline $true
  Network $true
  $null=Offline $false
  'PASS return from background reads current connectivity'
} finally {
  $mode=if($wifi -eq '1'){'enable'}else{'disable'}
  & $Adb -s $Device shell svc wifi $mode
  $mode=if($data -eq '1'){'enable'}else{'disable'}
  & $Adb -s $Device shell svc data $mode
}
