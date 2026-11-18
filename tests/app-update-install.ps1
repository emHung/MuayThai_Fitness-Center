param([string]$ApkPath='android/app/build/update-qa/probe.apk', [Parameter(Mandatory)][string]$ExpectedVersion)
$ErrorActionPreference='Stop'
$Adb="$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
$Device='emulator-5556'
$OutputDirectory='android/app/build/update-qa/screens'
if ((& $Adb -s $Device shell getprop ro.kernel.qemu).Trim() -ne '1') {throw 'Emulator required'}
$helpers=[IO.File]::ReadAllText('tests/capture-android.ps1')
Invoke-Expression $helpers.Substring($helpers.IndexOf('function Read-Ui'),$helpers.IndexOf('& $Adb -s $Device install')-$helpers.IndexOf('function Read-Ui'))
& $Adb -s $Device shell am force-stop vn.muaythai.app
& $Adb -s $Device install -r -d $ApkPath
if($LASTEXITCODE -ne 0){throw 'Install probe failed'}
& $Adb -s $Device shell pm clear vn.muaythai.app | Out-Null
Start-Sleep -Seconds 2
& $Adb -s $Device shell input keyevent 224
& $Adb -s $Device shell wm dismiss-keyguard
& $Adb -s $Device shell am start -W -n vn.muaythai.app/.LoginActivity | Out-Null
Expect-Text 'Cập nhật ngay'
Tap-Text 'Cập nhật ngay'
Capture 'UPDATE' 'download'
for($i=0;$i -lt 30;$i++) {
 $xml=Read-Ui
 if($xml.SelectSingleNode("//node[@package='com.android.settings']")){break}
 if($xml.SelectSingleNode("//node[contains(@text,'không hợp lệ') or contains(@text,'thất bại')]")){throw 'Download verification failed'}
 Start-Sleep -Seconds 1
}
$xml=Read-Ui
if(!$xml.SelectSingleNode("//node[@package='com.android.settings']")){throw 'Install permission screen absent'}
Capture 'UPDATE' 'install-permission'
$toggle=$xml.SelectSingleNode("//node[@class='android.widget.Switch']")
if(!$toggle){$toggle=$xml.SelectSingleNode("//node[@checkable='true']")}
Tap-Node $toggle
& $Adb -s $Device shell input keyevent 4
Start-Sleep -Seconds 2
$xml=Read-Ui
$installer=$xml.SelectSingleNode("//node[contains(@package,'packageinstaller')]" )
if(!$installer){throw 'Installer confirmation absent'}
Capture 'UPDATE' 'installer'
& $Adb -s $Device shell input keyevent 4
Expect-Text 'Cài đặt'
Capture 'UPDATE' 'install-cancelled'
'PASS real GitHub download, verification, permission and installer; cancellation returns to app'
Tap-Text 'Cài đặt'
Expect-Text 'Update'
Tap-Text 'Update'
for($i=0;$i -lt 30;$i++) {
 $package=(& $Adb -s $Device shell dumpsys package vn.muaythai.app) -join "`n"
 if($package.Contains('versionName='+$ExpectedVersion)){ 'PASS confirmed installation'; return }
 $xml=Read-Ui
 $scan=$xml.SelectSingleNode("//node[@text='Scan app']")
 if($scan){Tap-Node $scan}
 if($xml.SelectSingleNode("//node[@text='This app looks safe']")){Tap-Node ($xml.SelectSingleNode("//node[@text='Install']"))}
 Start-Sleep -Seconds 2
}
throw 'Installation has not completed; inspect Android/Play Protect confirmation'