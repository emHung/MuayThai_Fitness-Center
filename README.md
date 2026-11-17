# MuayThai _Fitness Center

**Tiến độ bàn giao** · **████████░░░░ 4/6 mốc hoàn thành**. [Chi tiết và nhật ký cập nhật](PROGRESS.md)

Ứng dụng Android Java cho quản trị viên, huấn luyện viên và học viên. Backend chạy trong Docker Compose gồm Auth, Schedule, Check-in, Progress và API Gateway. Các dịch vụ lưu dữ liệu trong các database riêng trên PostgreSQL.

## Cập nhật Git hằng ngày

Ghi thay đổi và bằng chứng kiểm thử vào [nhật ký tiến độ](PROGRESS.md) cùng commit của ngày đó. Công việc định kỳ trên Windows kiểm tra và đẩy các commit đã có trên `main`. Có thể chạy tệp [push-daily.ps1](scripts/push-daily.ps1) với `-WhatIf` để xem trước.

## Chạy bản demo

Dùng PowerShell và Docker Desktop. Từ thư mục dự án, chạy các lệnh sau.

```powershell
.\deploy\init-demo.ps1
docker compose -f deploy/compose.yaml up -d --build --wait
Invoke-RestMethod http://127.0.0.1:8080/actuator/health
```

Gateway cục bộ lắng nghe tại `127.0.0.1:8080`. Flyway tự áp dụng migration. Trình khởi tạo tạo tài khoản demo khi database mới và lưu mật khẩu sinh ngẫu nhiên trong `deploy/.env`. Backend dùng JDK 21 trong Docker.

| Vai trò | Tài khoản demo | Biến mật khẩu trong `deploy/.env` |
| --- | --- | --- |
| Quản trị viên | `admin@example.test` | `DEMO_ADMIN_PASSWORD` |
| Huấn luyện viên | `trainer@example.test` | `DEMO_TRAINER_PASSWORD` |
| Học viên | `learner@example.test` | `DEMO_LEARNER_PASSWORD` |

Huấn luyện viên dùng mật khẩu tạm phải đổi khi đăng nhập lần đầu. Nếu đã đổi, dùng mật khẩu mới. Quản trị viên thiết lập ca làm cho HLV trước khi mở lịch, xếp lớp hoặc đặt PT. Gói PT và lớp 10 theo HLV, gói lớp 30 theo lớp.

## Build và cài Android

Dùng JDK 17 và Android SDK 36. Mở thư mục `android` trong Android Studio hoặc chạy các lệnh sau. Bật gỡ lỗi USB nếu cài lên thiết bị thật.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
Set-Location android
.\gradlew.bat :app:assembleDebug
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
& $adb reverse tcp:8080 tcp:8080
& $adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

Nếu có nhiều thiết bị, chỉ định thiết bị với `adb -s <serial>`. APK debug mặc định dùng loopback HTTP qua ADB. Khi có máy chủ HTTPS, đặt `-PauthBaseUrl=https://<tên-miền>/` lúc build. Phiên hiện lưu trong bộ nhớ của ứng dụng và bị thu hồi trên máy chủ khi đăng xuất.

## Luồng trình diễn

1. Quản trị viên tạo hồ sơ HLV và học viên, nhập ca làm rồi cấp gói PT hoặc gói lớp.
2. Học viên đặt PT, HLV duyệt. Với lớp 10, HLV đề xuất lớp, quản trị viên mở lớp và học viên đăng ký buổi.
3. Học viên mở mã QR gói. Quản trị viên mở mục Điểm danh và xác nhận gói cùng buổi hợp lệ. Màn hình lịch sử hiển thị lượt còn lại và điều chỉnh.
4. HLV điền nhận xét sau buổi và cập nhật mục tiêu. Học viên xem trong Hành trình. Thông báo của trung tâm xuất hiện trong hộp thư.

## Kiểm thử API

Script `tests/acceptance.mjs` tạo tài khoản, lớp và gói có nhãn QA. Chạy với Node.js trên một Compose project thử nghiệm có database riêng.

```powershell
$env:GATEWAY_PORT = '8180'
$env:CLASS_CALENDAR_CRON = '*/5 * * * * *'
docker compose -p khang-acceptance -f deploy/compose.yaml up -d --build --wait
$env:MUAYTHAI_TEST_URL = 'http://127.0.0.1:8180'
$env:MUAYTHAI_TEST_CALENDAR = '1'
$env:MUAYTHAI_TEST_RECOVERY = '1'
node tests/acceptance.mjs
docker compose -p khang-acceptance -f deploy/compose.yaml down
Remove-Item Env:GATEWAY_PORT, Env:CLASS_CALENDAR_CRON, Env:MUAYTHAI_TEST_URL, Env:MUAYTHAI_TEST_CALENDAR, Env:MUAYTHAI_TEST_RECOVERY
```

## Sao lưu dữ liệu

Dùng `deploy/backup.ps1` để xuất PostgreSQL ra tệp SQL. Chọn thư mục sao lưu nằm ngoài thư mục mã nguồn. Tệp chứa dữ liệu nghiệp vụ và thông tin tài khoản. Khi cần bản sao nhất quán giữa các database, tạm dừng các dịch vụ ghi dữ liệu trong lúc sao lưu.

```powershell
$archive = Join-Path $env:LOCALAPPDATA 'MuayThai\Backups\demo.sql'
.\deploy\backup.ps1 -Destination $archive
```

Phục hồi bằng `psql -X -v ON_ERROR_STOP=1 -d postgres -f <tệp-sao-lưu>` trên cụm PostgreSQL trống, dùng tài khoản quản trị phục hồi riêng. Tệp giữ cấu trúc và dữ liệu của các database. Đặt lại mật khẩu của các vai trò kết nối theo cấu hình của môi trường mới trước khi khởi động dịch vụ.

Mã Android nằm trong `android/app/src/main`. Mỗi module backend nằm dưới `backend`. Tài liệu thiết kế tham chiếu nằm trong `docs`.

[Trạng thái bàn giao](docs/DEMO_STATUS.md) · [Tổng quan tài liệu](docs/README.md) · [Hợp đồng API](docs/API.md) · [Kế hoạch và tiêu chí nghiệm thu](docs/IMPLEMENTATION.md)
