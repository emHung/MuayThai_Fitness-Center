[CmdletBinding()]
param(
  [Parameter(Mandatory)][string] $ApkPath,
  [string] $Device = 'emulator-5556',
  [string] $Adb = (Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe')
)
$ErrorActionPreference = 'Stop'
if ((& $Adb -s $Device shell getprop ro.kernel.qemu).Trim() -ne '1') {
  throw 'Update regression requires an isolated emulator.'
}
function Read-Ui {
  & $Adb -s $Device shell uiautomator dump /sdcard/update-test.xml | Out-Null
  [xml]$xml = (& $Adb -s $Device exec-out cat /sdcard/update-test.xml) -join ''
  return $xml
}
function Tap($node) {
  if (!$node -or $node.bounds -notmatch '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') { throw 'UI element unavailable' }
  & $Adb -s $Device shell input tap ([int](([int]$Matches[1]+[int]$Matches[3])/2)) ([int](([int]$Matches[2]+[int]$Matches[4])/2))
}
function Expect-Prompt([bool]$visible) {
  $found = $false
  for ($i=0; $i -lt 5; $i++) {
    $xml = Read-Ui
    $found = $null -ne $xml.SelectSingleNode("//node[@text='Cập nhật ngay']")
    if ($found -eq $visible) { return }
    Start-Sleep -Seconds 1
  }
  throw "Update dialog expected visible=$visible, actual=$found"
}
function Open-App {
  & $Adb -s $Device shell am start -W -n vn.muaythai.app/.LoginActivity | Out-Null
}
& $Adb -s $Device install -r $ApkPath | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'APK installation failed' }
# Clears only the disposable emulator's app state, never a physical phone.
& $Adb -s $Device shell pm clear vn.muaythai.app | Out-Null
Open-App
Expect-Prompt $true
'PASS initial GitHub update prompt'
& $Adb -s $Device shell input keyevent 3
Open-App
Expect-Prompt $true
'PASS prompt survives leaving and returning without choosing Later'
& $Adb -s $Device shell am force-stop vn.muaythai.app
Open-App
Expect-Prompt $true
'PASS prompt survives process restart during check interval'
$xml = Read-Ui
Tap ($xml.SelectSingleNode("//node[@text='Để sau']"))
& $Adb -s $Device shell input keyevent 3
Open-App
Expect-Prompt $false
'PASS explicit Later still snoozes the version'
