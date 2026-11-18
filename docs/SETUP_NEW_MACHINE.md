# Cài dự án trên máy mới

Hướng dẫn cho Windows, chạy lệnh trong PowerShell từ thư mục gốc sau khi clone. Bản mã nguồn hiện tại phát hành APK 0.1.7/code 8. Đây là môi trường phát triển/demo; hướng dẫn doanh nghiệp nằm ở [PRODUCTION.md](PRODUCTION.md).

## Chọn đúng nhu cầu

| Nhu cầu | Cần cài |
| --- | --- |
| Chỉ dùng app trên điện thoại | APK HTTPS ở Releases, Internet và máy chủ đang hoạt động. Không cần Git, Java, Docker hay Tailscale trên điện thoại khi dùng Funnel. |
| Chạy máy chủ demo trên máy mới | Git, PowerShell, Docker Desktop chạy Linux containers/Compose v2. |
| Sửa/build Android | Thêm JDK 17, Android Studio và Android SDK Platform 36, Build Tools 36.0.0, Platform Tools; emulator/system image nếu không dùng điện thoại USB. |
| Chạy bộ kiểm thử API | Thêm Node.js 24; dùng module tích hợp, không cần `npm install`. PowerShell 7 phù hợp với các script kiểm thử đa nền tảng. |
| Biên dịch backend ngoài Docker | JDK 21 và Maven 3.9.x. Không cần cài PostgreSQL riêng khi dùng Compose. |

Phiên bản được đối chiếu từ [Gradle Android](../android/app/build.gradle), [Maven backend](../backend/pom.xml), [Dockerfile](../backend/Dockerfile) và [workflow](../.github/workflows/verify.yaml). Không cần cài Gradle toàn máy: dùng wrapper đã có trong `android/`.

## 1. Chuẩn bị môi trường

- Cài [Git](https://git-scm.com/downloads), [PowerShell](https://learn.microsoft.com/powershell/scripting/install/installing-powershell-on-windows), [Docker Desktop](https://docs.docker.com/desktop/setup/install/windows-install/).
- Làm theo yêu cầu WSL 2/ảo hóa của Docker, khởi động lại máy nếu trình cài yêu cầu. Mở Docker Desktop và chờ Engine sẵn sàng, dùng Linux containers.
- Khi build app: cài [Android Studio](https://developer.android.com/studio), mở SDK Manager cài các gói SDK ở bảng trên. Ghi lại đường dẫn SDK thực tế; xem [hướng dẫn SDK Manager](https://developer.android.com/studio/intro/update#sdk-manager).
- Cài [JDK 17](https://adoptium.net/temurin/releases/?version=17) cho Android; nếu Android Studio đang chọn JDK khác, đặt Gradle JDK về 17. JDK 21 cho backend đã nằm trong Docker image.

Nếu cần kiểm thử API, cài [Node.js 24](https://nodejs.org/en/download). Nếu build backend ngoài Docker, cài [Maven](https://maven.apache.org/download.cgi) và JDK 21, kiểm tra `mvn --version` dùng đúng JDK. Các bản Java khác nhau có thể cùng cài; chọn JAVA_HOME đúng theo công việc.

Kiểm tra trong terminal mới:

```powershell
git --version
docker version
docker compose version
# Chỉ cần khi kiểm thử API:
node --version
```

`docker version` phải trả cả Client và Server. Cấu hình máy khuyến nghị của dự án: RAM 16 GB khi chạy đồng thời Docker và emulator; đây là gợi ý thực hành, không phải giới hạn phần mềm bắt buộc. Nếu máy yếu, dùng điện thoại USB thay emulator.

## 2. Clone và khởi tạo máy chủ

```powershell
git clone https://github.com/emHung/MuayThai_Fitness-Center.git
Set-Location MuayThai_Fitness-Center
.\deploy\init-demo.ps1
docker compose --env-file deploy/.env -p deploy -f deploy/compose.yaml up -d --build --wait --wait-timeout 300
if ($LASTEXITCODE -ne 0) { throw 'Docker chưa khởi động đầy đủ' }
docker compose --env-file deploy/.env -p deploy -f deploy/compose.yaml ps
Invoke-RestMethod http://127.0.0.1:8080/actuator/health
```

Kết quả mong đợi: health `UP`, database và dịch vụ có healthcheck đạt. Lần đầu cần Internet để tải image và dependency Maven. `init-demo.ps1` tạo `deploy/.env` nếu thiếu và bổ sung khóa còn thiếu; chạy lại không đổi giá trị đã có. Nếu PowerShell chặn script, xem chính sách của máy/tổ chức; không cần tắt bảo vệ toàn máy để cài dự án.

Không đưa `.env`, mật khẩu, bản sao database hoặc keystore lên Git. Dùng trình soạn thảo cục bộ xem ba biến `DEMO_ADMIN_PASSWORD`, `DEMO_TRAINER_PASSWORD`, `DEMO_LEARNER_PASSWORD` để đăng nhập lần đầu:

| Vai trò | Tài khoản ban đầu |
| --- | --- |
| ADMIN | `admin@example.test` |
| TRAINER | `trainer@example.test` |
| LEARNER | `learner@example.test` |

Mật khẩu được seed khi tạo tài khoản mới; sửa `.env` không tự đổi mật khẩu tài khoản đã có trong database. Tài khoản mật khẩu tạm phải đổi ở lần đầu. ADMIN tạo lễ tân trong app và thiết lập ca làm HLV trước khi đặt lịch.

## 3. Build Android

Đặt `JAVA_HOME` tới **thư mục JDK 17 thực tế trên máy mới**, không sao chép đường dẫn máy người khác. Ví dụ dưới đây giả sử JDK được cài ở `C:\Program Files\Java\jdk-17`; đổi nếu khác.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
# Nếu SDK nằm nơi khác, sửa ANDROID_HOME trước khi chạy.
& "$env:JAVA_HOME\bin\java.exe" -version
Set-Location android
.\gradlew.bat --version
.\gradlew.bat :app:assembleDebug :app:lintDebug
if ($LASTEXITCODE -ne 0) { throw 'Build hoặc lint thất bại' }
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
& $adb devices
& $adb -s emulator-5554 install -r .\app\build\outputs\apk\debug\app-debug.apk
Set-Location ..
```

Mở Device Manager trong Android Studio, tạo/bật emulator API 24 trở lên trước khi cài. Thay `emulator-5554` bằng serial từ `adb devices`. Nếu SDK báo thiếu license, mở SDK Manager để chấp nhận/cài các gói cần thiết. File `android/local.properties` do Studio sinh là cấu hình riêng của máy, không commit.

## 4. Chọn đường kết nối

| Thiết bị | Địa chỉ khi build | Ghi chú |
| --- | --- | --- |
| Emulator Android Studio | `http://10.0.2.2:8080/` mặc định | Trỏ về loopback máy chạy emulator và Docker. |
| Điện thoại USB | `http://127.0.0.1:8080/` | Cần `adb reverse` và debug APK. |
| Điện thoại qua Internet | URL HTTPS của máy chủ mới | Dùng Funnel hoặc reverse proxy HTTPS. |

Ví dụ điện thoại USB (bật Developer options/USB debugging và chấp nhận máy tính):

```powershell
Set-Location android
.\gradlew.bat :app:assembleDebug '-PauthBaseUrl=http://127.0.0.1:8080/'
& $adb -s <serial-dien-thoai> reverse tcp:8080 tcp:8080
& $adb -s <serial-dien-thoai> install -r .\app\build\outputs\apk\debug\app-debug.apk
Set-Location ..
```

Thay `<serial-dien-thoai>` trước khi chạy. HTTP chỉ được cho phép ở debug với các địa chỉ cục bộ đã khai báo trong [local_network.xml](../android/app/src/debug/res/xml/local_network.xml); không đổi sang IP LAN tùy ý rồi bỏ qua chính sách mạng.

Nếu dùng [Tailscale Funnel](https://tailscale.com/docs/features/tailscale-funnel), cài/đăng nhập Tailscale trên máy chủ mới, bật quyền Funnel theo tài khoản và chạy:

```powershell
tailscale funnel --bg http://127.0.0.1:8080
tailscale funnel status
```

Lấy **URL HTTPS được trả về trên máy mới** để build:

```powershell
Set-Location android
.\gradlew.bat :app:assembleDebug '-PauthBaseUrl=https://TEN-MAY-MOI.TAILNET.ts.net/' '-PappUpdateApkName=app-debug-tailscale.apk'
Set-Location ..
```

Thay URL mẫu bằng URL thật. APK HTTPS hiện có trên GitHub vẫn trỏ về máy chủ đã cấu hình lúc build; clone sang máy mới không tự đổi endpoint. Cần giữ URL cũ qua kế hoạch chuyển máy hoặc build/phát hành APK trỏ URL mới. Máy chủ phải bật, Docker và Funnel phải chạy. Funnel công khai gateway ra Internet; không dùng chế độ demo với dữ liệu khách hàng thật. Tailscale Serve chỉ phục vụ tailnet và có yêu cầu thiết bị khác với Funnel.

## 5. Kiểm thử trên database riêng

Không chạy `acceptance.mjs` hoặc `images.mjs` vào database đang phục vụ người dùng: chúng tạo/sửa dữ liệu thử.

```powershell
$env:GATEWAY_PORT = '8180'
$env:MUAYTHAI_TEST_URL = 'http://127.0.0.1:8180'
$env:MUAYTHAI_TEST_COMPOSE_PROJECT = 'muaythai-qa'
try {
    docker compose --env-file deploy/.env -p muaythai-qa -f deploy/compose.yaml up -d --build --wait --wait-timeout 300
    if ($LASTEXITCODE -ne 0) { throw 'QA stack chưa sẵn sàng' }
    node tests/acceptance.mjs
    if ($LASTEXITCODE -ne 0) { throw 'Acceptance thất bại' }
    node tests/images.mjs
    if ($LASTEXITCODE -ne 0) { throw 'Kiểm thử ảnh thất bại' }
} finally {
    # Chỉ xóa database QA vừa tạo, không dùng project deploy ở lệnh này.
    docker compose --env-file deploy/.env -p muaythai-qa -f deploy/compose.yaml down --volumes
    Remove-Item Env:GATEWAY_PORT, Env:MUAYTHAI_TEST_URL, Env:MUAYTHAI_TEST_COMPOSE_PROJECT -ErrorAction SilentlyContinue
}
```

Các ca lịch định kỳ/phục hồi tùy chọn và production smoke được cấu hình trong [workflow](../.github/workflows/verify.yaml). Các script emulator nằm trong [danh mục kiểm thử](TESTING.md).

## 6. Dữ liệu, cập nhật và lỗi thường gặp

- Dừng demo mà giữ dữ liệu: `docker compose --env-file deploy/.env -p deploy -f deploy/compose.yaml down` (không thêm `--volumes`).
- Sao lưu: `.\deploy\backup.ps1 -Project deploy -Destination .\backups\demo-before-update.sql`. Bảo quản file sao lưu và `.env` ngoài Git; khôi phục sang môi trường thử trước khi chuyển dữ liệu thật.
- Chạy `git pull --ff-only`, đọc thay đổi migration, sao lưu rồi mới rebuild Docker khi nâng máy chủ. Không sửa migration đã áp dụng.
- Trên máy build mới, Android tự tạo debug key mới: APK sẽ không cài đè APK debug cũ nếu khác khóa. Để tiếp tục chuỗi phát hành cần nhận khóa cũ qua kênh bảo mật; không chia sẻ khóa trong repo. Không gỡ app để xử lý lỗi chữ ký nếu chưa cân nhắc dữ liệu cục bộ.
- `connection refused`: kiểm tra Docker Engine, `compose ps` và health trước; xem `docker compose --env-file deploy/.env -p deploy -f deploy/compose.yaml logs --tail 100 gateway`.
- Cổng 8080 bận: đổi `GATEWAY_PORT` nhất quán và build lại endpoint; `adb reverse` cũng phải dùng đúng cổng.
- Điện thoại không kết nối: thử URL health trên trình duyệt điện thoại, kiểm tra endpoint trong APK và `tailscale funnel status`. Không sửa xác thực để chữa lỗi mạng.
- Không cần cài RTK, graft, Codex hay plugin để chạy dự án. Không cần bật SonarQube cho lần khởi động đầu; hướng dẫn chất lượng mã nằm ở [TESTING.md](TESTING.md).

## Nghiệm thu trên máy mới

Chỉ coi việc chuyển máy đạt sau khi health UP; đăng nhập/đổi mật khẩu; tạo lễ tân; thiết lập ca; cấp gói/đặt lịch/duyệt/điểm danh; kiểm lượt còn lại; chạy kiểm thử QA riêng và thử sao lưu/khôi phục. Hướng dẫn đã đối chiếu mã cấu hình; chưa phải bằng chứng đã nghiệm thu trên một máy Windows vật lý mới hoàn toàn.
