[CmdletBinding()]
param(
  [Parameter(Mandatory)][string] $FixturePath,
  [Parameter(Mandatory)][string] $ApkPath,
  [string] $Device='emulator-5556',
  [string] $Adb=(Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'),
  [string] $OutputDirectory='android/app/build/ui15-qa/screens'
)
$ErrorActionPreference='Stop'
# Reuse the guarded four-role workflow and its UI helpers before checking layout interactions.
. "$PSScriptRoot/capture-android.ps1" -FixturePath $FixturePath -ApkPath $ApkPath -Device $Device -Adb $Adb -OutputDirectory $OutputDirectory
function Loaded {
  for($i=0;$i -lt 12;$i++) {
    $xml=Read-Ui
    if(!$xml.SelectSingleNode("//node[@resource-id='vn.muaythai.app:id/loading']")){return $xml}
    Start-Sleep -Milliseconds 300
  }
  throw 'Screen did not finish loading'
}
function Top {
  1..3 | ForEach-Object { & $Adb -s $Device shell input swipe 350 300 350 1050 150 }
}
function Find([string]$text) {
  $null=Loaded
  Top
  for($i=0;$i -lt 7;$i++) {
    $xml=Read-Ui
    $node=$xml.SelectSingleNode("//node[@text='$text' or @content-desc='$text']")
    if($node){return $node}
    & $Adb -s $Device shell input swipe 350 1050 350 400 200
  }
  throw "Element unavailable: $text"
}
function Press([string]$text){Tap-Node (Find $text)}
function FindContaining([string]$text) {
  $null=Loaded
  Top
  for($i=0;$i -lt 7;$i++) {
    $xml=Read-Ui
    $node=$xml.SelectSingleNode("//node[contains(@text,'$text')]")
    if($node){return $node}
    & $Adb -s $Device shell input swipe 350 1050 350 400 200
  }
  throw 'Requested data row unavailable'
}
function Back { & $Adb -s $Device shell input keyevent 4; $null=Loaded }
function Tab([string]$text) {
  $xml=Read-Ui
  Tap-Node ($xml.SelectSingleNode("//node[@resource-id='vn.muaythai.app:id/workspaceNav']//node[@text='$text']"))
  $null=Loaded
}
function Account {
  $xml=Read-Ui
  Tap-Node ($xml.SelectSingleNode("//node[@resource-id='vn.muaythai.app:id/accounts']"))
  $null=Loaded
}
function LoginRole([string]$role) {
  Expect-Text 'Đăng nhập'
  $account=$fixture.accounts.$role
  Enter-Field email $account.username
  Enter-Field password $account.password
  $xml=Read-Ui
  Tap-Node ($xml.SelectSingleNode("//node[@resource-id='vn.muaythai.app:id/submit']"))
  $null=Loaded
}

$null=Find 'Đăng xuất'
Capture 'LEARNER' 'profile-options'
Press 'Đổi mật khẩu'
Capture 'LEARNER' 'password-form'
Back
Press 'Thông tin ứng dụng'
Expect-Text 'Phiên bản 0.1.5'
Capture 'LEARNER' 'app-information'
Back
Back
Tab 'Lịch'
$xml=Read-Ui
$date=$xml.SelectNodes("//node[@class='android.widget.EditText']") | Where-Object {$_.text -match '^\d{4}-\d{2}-\d{2}$'} | Select-Object -First 1
if(!$date){throw 'Calendar date field unavailable'}
$old=[datetime]::ParseExact($date.text,'yyyy-MM-dd',[Globalization.CultureInfo]::InvariantCulture)
$target=if($old.DayOfWeek -eq 'Monday'){$old.AddDays(1)}else{$old.AddDays(-1)}
$label=if($target.DayOfWeek -eq 'Sunday'){'CN'}else{'T'+([int]$target.DayOfWeek+1)}
$dayText=$label+"`n"+$target.Day
Tap-Node ($xml.SelectSingleNode("//node[@text='$dayText']"))
$xml=Loaded
$value=$target.ToString('yyyy-MM-dd')
if(!$xml.SelectSingleNode("//node[@class='android.widget.EditText' and @text='$value']")){throw 'Week selector did not change date'}
Press 'Xem theo tuần'
$null=Loaded
Capture 'LEARNER' 'calendar-selected-week'
& $Adb -s $Device shell input swipe 350 1050 350 350 300
Capture 'LEARNER' 'calendar-week-results'
'PASS week strip updates date and week mode loads real sessions'
Tab 'Đặt lịch'
Press 'Đặt PT với HLV phụ trách'
Expect-Text 'Chọn gói'
Capture 'LEARNER' 'package-picker'
$xml=Read-Ui
$item=$xml.SelectSingleNode("//node[@class='android.widget.ListView']//node[@class='android.widget.TextView']")
Tap-Node $item
$null=Loaded
Capture 'LEARNER' 'pt-booking-form'
Back
Tab 'Thẻ QR'
$xml=Read-Ui
Tap-Node ($xml.SelectSingleNode("//node[starts-with(@text,'PT')]"))
$null=Loaded
Capture 'LEARNER' 'package-qr-detail'
'PASS PT package selection, booking form and QR detail open'
Account
Press 'Đăng xuất'
LoginRole 'ADMIN'
Expect-Text 'Quét QR check-in'
Tab 'Quản lý'
Press 'Lớp nhóm'
Press 'Tạo lớp 30'
Capture 'ADMIN' 'class30-form'
Back
Back
Tab 'Học viên'
$email=$fixture.accounts.LEARNER.username
Tap-Node (FindContaining $email)
$null=Loaded
Capture 'ADMIN' 'learner-detail'
'PASS management creation form and learner detail open'
Back
Account
Press 'Lưu thông tin'
$null=Loaded
Capture 'ADMIN' 'profile-after-save'
'PASS existing profile values save through the redesigned form'

$font=(& $Adb -s $Device shell settings get system font_scale).Trim()
try {
  & $Adb -s $Device shell wm size 640x1136
  & $Adb -s $Device shell settings put system font_scale 1.3
  Start-Sleep -Seconds 2
  $null=Loaded
  # Configuration recreation may return to the role's first tab.
  $xml=Read-Ui
  if($xml.SelectSingleNode("//node[@resource-id='vn.muaythai.app:id/back']")){Back}
  Tab 'Quản lý'
  Capture 'ADMIN' 'management-320dp-font130'
  Account
  Capture 'ADMIN' 'profile-320dp-font130'
  'PASS narrow 320dp layout and 130 percent font screenshots'
} finally {
  & $Adb -s $Device shell settings put system font_scale $font
  & $Adb -s $Device shell wm size reset
}
